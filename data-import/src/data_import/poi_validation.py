"""Offline TourAPI sample collection and validation for POINT_OF_INTEREST.

TourAPI categories are evidence, not Canonical answers.  In particular, this
module never treats a missing programme or event in a list response as proof
that a place is view-only.
"""

from __future__ import annotations

import csv
import json
import random
from collections.abc import Iterable
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

POI_KINDS = {
    "HISTORIC_SITE",
    "TEMPLE_RELIGIOUS",
    "MOUNTAIN",
    "FOREST_RECREATION",
    "WATERFALL",
    "VALLEY",
    "LAKE_RIVER",
    "ISLAND",
    "BEACH",
    "PARK",
    "OBSERVATORY",
    "STREET_VILLAGE",
    "MUSEUM",
    "ART_MUSEUM",
    "PERFORMANCE_HALL",
    "EXHIBITION_CENTER",
}
PRIMARY_TAXONOMIES = {
    "POINT_OF_INTEREST",
    "ACTIVITY",
    "SHOP",
    "ACCOMMODATION",
    "UNCLASSIFIED",
}
REVIEW_STATUSES = {"PENDING", "AUTO_PROPOSED", "NEEDS_MANUAL", "REVIEWED"}
PRESENCE_STATES = {"UNKNOWN", "CONFIRMED_PRESENT"}
REQUIRED_COLUMNS = {
    "sample_id",
    "place_name",
    "source_provider",
    "source_place_id",
    "source_content_type_id",
    "source_category",
    "primary_taxonomy",
    "poi_kind",
    "activity_offering_state",
    "activity_offering_evidence",
    "event_state",
    "event_evidence",
    "evidence",
    "review_status",
    "notes",
}


class PoiValidationError(ValueError):
    """Raised when a POI sample CSV or TourAPI raw page is not usable."""


@dataclass(frozen=True)
class ValidationIssue:
    sample_id: str
    severity: str
    code: str
    message: str


@dataclass(frozen=True)
class ValidationSummary:
    samples: int
    poi_samples: int
    valid: int
    needs_review: int
    invalid: int
    issues: list[ValidationIssue]

    def to_dict(self) -> dict[str, Any]:
        result = asdict(self)
        result["issues"] = [asdict(issue) for issue in self.issues]
        return result


def load_samples(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames is None:
            raise PoiValidationError("sample CSV has no header")
        missing = REQUIRED_COLUMNS - set(reader.fieldnames)
        if missing:
            raise PoiValidationError(f"sample CSV is missing columns: {', '.join(sorted(missing))}")
        return [{key: (value or "").strip() for key, value in row.items()} for row in reader]


def write_tour_api_poi_candidates(
    raw_root: Path,
    mapping_path: Path,
    output: Path,
    *,
    per_kind: int = 10,
    seed: int = 20260824,
) -> int:
    """Create a deterministic, stratified POI review CSV from TourAPI list pages.

    Only confirmed, clearly POI-shaped provider mappings seed a POI proposal.
    Theme parks, general tourist spots, experience centres and events are
    deliberately omitted: their provider subtype is not sufficient evidence.
    """
    if per_kind < 1:
        raise ValueError("per_kind must be at least 1")

    mappings = _load_safe_poi_mappings(mapping_path)
    pools: dict[str, list[dict[str, str]]] = {kind: [] for kind in POI_KINDS}
    seen_source_ids: set[tuple[str, str]] = set()
    for content_type, page_path in _iter_poi_pages(raw_root):
        for item in _page_items(page_path):
            source_id = str(item.get("contentid", "")).strip()
            name = str(item.get("title", "")).strip()
            source_category = str(item.get("lclsSystm3", "")).strip()
            key = (content_type, source_category)
            poi_kind = mappings.get(key)
            identity = (content_type, source_id)
            if not source_id or not name or poi_kind is None or identity in seen_source_ids:
                continue
            seen_source_ids.add(identity)
            pools[poi_kind].append(
                {
                    "sample_id": f"TOUR_API-{content_type}-{source_id}",
                    "place_name": name,
                    "source_provider": "TOUR_API",
                    "source_place_id": source_id,
                    "source_content_type_id": content_type,
                    "source_category": source_category,
                    "primary_taxonomy": "POINT_OF_INTEREST",
                    "poi_kind": poi_kind,
                    "activity_offering_state": "UNKNOWN",
                    "activity_offering_evidence": "",
                    "event_state": "UNKNOWN",
                    "event_evidence": "",
                    "evidence": (
                        f"TourAPI contentTypeId={content_type}; lclsSystm3={source_category}; "
                        f"confirmed POI mapping proposes poi_kind={poi_kind}"
                    ),
                    "review_status": "AUTO_PROPOSED",
                    "notes": (
                        "Confirm the place and poi_kind from authoritative detail. "
                        "UNKNOWN offering/event does not mean absent."
                    ),
                }
            )

    rng = random.Random(seed)
    selected: list[dict[str, str]] = []
    for poi_kind in sorted(pools):
        candidates = pools[poi_kind]
        rng.shuffle(candidates)
        selected.extend(candidates[:per_kind])
    if not selected:
        raise PoiValidationError(
            "no usable POI records found; collect TourAPI content types 12 and/or 14 first"
        )
    write_samples(output, selected)
    return len(selected)


def validate_samples(
    samples: list[dict[str, str]], *, minimum_samples: int = 100
) -> ValidationSummary:
    if minimum_samples < 1:
        raise ValueError("minimum_samples must be at least 1")
    issues: list[ValidationIssue] = []
    sample_ids: set[str] = set()
    valid = needs_review = invalid = poi_samples = 0
    if len(samples) < minimum_samples:
        issues.append(
            ValidationIssue(
                "__dataset__",
                "ERROR",
                "INSUFFICIENT_SAMPLE_SIZE",
                f"at least {minimum_samples} samples are required; found {len(samples)}",
            )
        )

    for row in samples:
        sample_id = row["sample_id"]
        row_issues: list[ValidationIssue] = []
        if not sample_id:
            row_issues.append(
                ValidationIssue(sample_id, "ERROR", "MISSING_ID", "sample_id is required")
            )
        elif sample_id in sample_ids:
            row_issues.append(
                ValidationIssue(sample_id, "ERROR", "DUPLICATE_ID", "sample_id must be unique")
            )
        sample_ids.add(sample_id)
        if not row["place_name"]:
            row_issues.append(
                ValidationIssue(sample_id, "ERROR", "MISSING_NAME", "place_name is required")
            )
        if not row["source_provider"] or not row["source_place_id"]:
            row_issues.append(
                ValidationIssue(
                    sample_id, "ERROR", "MISSING_SOURCE", "provider and place ID are required"
                )
            )
        if row["source_content_type_id"] not in {"12", "14"}:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "INVALID_SOURCE_TYPE",
                    "POI samples must use TourAPI content type 12 or 14",
                )
            )
        if not row["source_category"]:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "MISSING_SOURCE_CATEGORY",
                    "record lclsSystm3 used to select the sample",
                )
            )

        primary = row["primary_taxonomy"].upper()
        if primary not in PRIMARY_TAXONOMIES:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "INVALID_PRIMARY_TAXONOMY",
                    "unsupported Canonical primary taxonomy",
                )
            )
        elif primary == "POINT_OF_INTEREST":
            poi_samples += 1
        poi_kind = row["poi_kind"].upper()
        if poi_kind and poi_kind not in POI_KINDS:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "UNKNOWN_POI_KIND",
                    "poi_kind is not in the v0.1 vocabulary",
                )
            )
        if primary == "POINT_OF_INTEREST" and not poi_kind:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "MISSING_POI_KIND",
                    "reviewed POI needs a poi_kind or a review note",
                )
            )
        if primary != "POINT_OF_INTEREST" and poi_kind:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "POI_KIND_ON_NON_POI",
                    "poi_kind must be blank when the final primary is not POINT_OF_INTEREST",
                )
            )

        for state_field, evidence_field, label in (
            ("activity_offering_state", "activity_offering_evidence", "ActivityOffering"),
            ("event_state", "event_evidence", "Event"),
        ):
            state = row[state_field].upper()
            if state not in PRESENCE_STATES:
                row_issues.append(
                    ValidationIssue(
                        sample_id,
                        "ERROR",
                        "INVALID_PRESENCE_STATE",
                        f"{state_field} must be UNKNOWN or CONFIRMED_PRESENT",
                    )
                )
            elif state == "CONFIRMED_PRESENT" and not row[evidence_field]:
                row_issues.append(
                    ValidationIssue(
                        sample_id,
                        "ERROR",
                        "MISSING_PRESENCE_EVIDENCE",
                        f"confirmed {label} needs evidence",
                    )
                )
            elif state == "UNKNOWN" and row[evidence_field]:
                row_issues.append(
                    ValidationIssue(
                        sample_id,
                        "WARNING",
                        "UNUSED_PRESENCE_EVIDENCE",
                        f"{evidence_field} is present while state is UNKNOWN",
                    )
                )

        review_status = row["review_status"].upper()
        if review_status not in REVIEW_STATUSES:
            row_issues.append(
                ValidationIssue(
                    sample_id, "ERROR", "INVALID_REVIEW_STATUS", "unsupported review status"
                )
            )
        elif review_status != "REVIEWED":
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "REVIEW_PENDING",
                    "classification still needs authoritative review",
                )
            )
        if not row["evidence"]:
            row_issues.append(
                ValidationIssue(
                    sample_id, "WARNING", "MISSING_EVIDENCE", "record classification evidence"
                )
            )

        issues.extend(row_issues)
        if any(issue.severity == "ERROR" for issue in row_issues):
            invalid += 1
        elif row_issues:
            needs_review += 1
        else:
            valid += 1

    return ValidationSummary(len(samples), poi_samples, valid, needs_review, invalid, issues)


def write_samples(path: Path, samples: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=sorted(REQUIRED_COLUMNS))
        writer.writeheader()
        writer.writerows(samples)


def write_report(path: Path, summary: ValidationSummary) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(summary.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8")


def _load_safe_poi_mappings(path: Path) -> dict[tuple[str, str], str]:
    required = {
        "source_content_type_id",
        "source_lcls3_code",
        "target_subtype_code",
        "mapping_status",
    }
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise PoiValidationError(
                f"mapping CSV is missing columns: {', '.join(sorted(missing))}"
            )
        result: dict[tuple[str, str], str] = {}
        for row in reader:
            content_type = (row.get("source_content_type_id") or "").strip()
            category = (row.get("source_lcls3_code") or "").strip()
            poi_kind = (row.get("target_subtype_code") or "").strip()
            status = (row.get("mapping_status") or "").strip()
            if content_type in {"12", "14"} and status == "CONFIRMED" and poi_kind in POI_KINDS:
                result[(content_type, category)] = poi_kind
    return result


def _iter_poi_pages(raw_root: Path) -> Iterable[tuple[str, Path]]:
    for content_type, directory in (("12", "12_관광지"), ("14", "14_문화시설")):
        yield from (
            (content_type, path) for path in sorted((raw_root / directory).rglob("page-*.json"))
        )


def _page_items(path: Path) -> list[dict[str, Any]]:
    try:
        page = json.loads(path.read_text(encoding="utf-8"))
        items = page["response"]["body"].get("items", {}).get("item", [])
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        raise PoiValidationError(f"invalid TourAPI raw page: {path}") from error
    if isinstance(items, dict):
        return [items]
    if not isinstance(items, list):
        raise PoiValidationError(f"TourAPI items are not a list: {path}")
    return [item for item in items if isinstance(item, dict)]
