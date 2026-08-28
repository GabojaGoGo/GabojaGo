"""Offline validation helpers for FOOD_BEVERAGE taxonomy review samples.

This module deliberately does not infer a taxonomy from a place name.  A reviewer
records evidence from the source and the proposed classification; the validator
then makes omissions, invalid combinations, and legacy labels visible.
"""

from __future__ import annotations

import csv
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

RESTAURANT_CUISINES = {
    "KOREAN",
    "CHINESE",
    "JAPANESE",
    "WESTERN",
    "ASIAN",
    "MIDDLE_EASTERN",
    "LATIN_AMERICAN",
    "FUSION_OTHER",
    "UNCLASSIFIED",
}
RESTAURANT_DINING_FORMATS = {
    "TABLE_GRILL",
    "BUFFET",
    "COURSE_DINING",
}
CUISINE_VARIANTS = {
    "CHINESE": {"KOREAN_CHINESE", "SICHUAN", "CANTONESE", "TAIWANESE", "HONG_KONG_STYLE", "BEIJING_STYLE"},
    "KOREAN": {"GANGWON", "JEJU", "JEOLLA", "GYEONGSANG"},
    "ASIAN": {"VIETNAMESE", "THAI", "INDIAN", "NEPALESE"},
}
PLACE_PRIMARIES = {
    "RESTAURANT",
    "CAFE",
    "BAR",
    "SHOPPING",
    "ACTIVITY",
    "POINT_OF_INTEREST",
    "ACCOMMODATION",
}
CAFE_PRIMARY_BEVERAGES = {"COFFEE", "TEA", "JUICE_SMOOTHIE", "OTHER_SPECIALTY_DRINK"}
CAFE_OFFERINGS = {
    "COFFEE",
    "TEA",
    "JUICE_SMOOTHIE",
    "BAKERY",
    "CAKE_DESSERT",
    "ICE_CREAM",
    "BINGSU",
    "BRUNCH",
}
CAFE_SERVICE_STYLES = {
    "TAKEOUT_FOCUSED",
    "STAY_FOCUSED",
    "ROASTERY",
    "SPECIALTY_COFFEE",
}
LEGACY_CODES = {
    "GENERAL_RESTAURANT",
    "SNACK_FAST_FOOD",
    "GENERAL_CAFE",
    "BAKERY_DESSERT_CAFE",
    "TRADITIONAL_TEA",
}
REVIEW_STATUSES = {"PENDING", "AUTO_PROPOSED", "NEEDS_MANUAL", "REVIEWED"}
PENDING_REVIEW_STATUSES = {"PENDING", "AUTO_PROPOSED", "NEEDS_MANUAL"}
REQUIRED_COLUMNS = {
    "sample_id",
    "place_name",
    "source_provider",
    "source_place_id",
    "source_url",
    "source_category",
    "primary_taxonomy",
    "cuisine",
    "cuisine_variant",
    "dining_format",
    "signature_menu",
    "primary_beverage",
    "offering",
    "cafe_service_style",
    "evidence",
    "review_status",
    "notes",
}


class TaxonomyValidationError(ValueError):
    """Raised when a review CSV itself cannot be read safely."""


@dataclass(frozen=True)
class ValidationIssue:
    sample_id: str
    severity: str
    code: str
    message: str


@dataclass(frozen=True)
class ValidationSummary:
    samples: int
    restaurant_samples: int
    cafe_samples: int
    valid: int
    needs_review: int
    invalid: int
    issues: list[ValidationIssue]

    def to_dict(self) -> dict[str, Any]:
        result = asdict(self)
        result["issues"] = [asdict(issue) for issue in self.issues]
        return result


def split_values(value: str) -> list[str]:
    values = [part.strip().upper() for part in value.split("|") if part.strip()]
    if len(values) != len(set(values)):
        raise TaxonomyValidationError(f"duplicate multi-value facet: {value}")
    return values


def load_samples(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames is None:
            raise TaxonomyValidationError("sample CSV has no header")
        missing = REQUIRED_COLUMNS - set(reader.fieldnames)
        if missing:
            raise TaxonomyValidationError(
                f"sample CSV is missing columns: {', '.join(sorted(missing))}"
            )
        return [{key: (value or "").strip() for key, value in row.items()} for row in reader]


def validate_samples(samples: list[dict[str, str]], *, minimum_samples: int = 100) -> ValidationSummary:
    if minimum_samples < 1:
        raise ValueError("minimum_samples must be at least 1")
    issues: list[ValidationIssue] = []
    sample_ids: set[str] = set()
    valid = needs_review = invalid = restaurant_samples = cafe_samples = 0

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
            row_issues.append(ValidationIssue(sample_id, "ERROR", "MISSING_ID", "sample_id is required"))
        elif sample_id in sample_ids:
            row_issues.append(
                ValidationIssue(sample_id, "ERROR", "DUPLICATE_ID", "sample_id must be unique")
            )
        sample_ids.add(sample_id)

        primary = row["primary_taxonomy"].upper()
        if primary not in PLACE_PRIMARIES:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "INVALID_PRIMARY_TAXONOMY",
                    "primary_taxonomy must be a supported Canonical place type",
                )
            )
        elif primary == "RESTAURANT":
            restaurant_samples += 1
        elif primary == "CAFE":
            cafe_samples += 1

        if not row["place_name"]:
            row_issues.append(ValidationIssue(sample_id, "ERROR", "MISSING_NAME", "place_name is required"))
        if not row["source_provider"] or not (row["source_place_id"] or row["source_url"]):
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "MISSING_SOURCE",
                    "source_provider and source_place_id or source_url are required",
                )
            )
        if not row["source_category"]:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "MISSING_SOURCE_CATEGORY",
                    "record the provider category or source tag used to select this sample",
                )
            )
        review_status = row["review_status"].upper()
        if review_status not in REVIEW_STATUSES:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "INVALID_REVIEW_STATUS",
                    "review_status must be PENDING, AUTO_PROPOSED, NEEDS_MANUAL, or REVIEWED",
                )
            )
        if not row["evidence"]:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "MISSING_EVIDENCE",
                    "record the menu, provider category, or other classification evidence",
                )
            )
        if review_status in PENDING_REVIEW_STATUSES:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "REVIEW_PENDING",
                    "classification is a proposal and still needs external evidence review",
                )
            )

        try:
            cuisine = split_values(row["cuisine"])
            cuisine_variant = split_values(row["cuisine_variant"])
            dining_format = split_values(row["dining_format"])
            signature_menu = split_values(row["signature_menu"])
            primary_beverage = split_values(row["primary_beverage"])
            offering = split_values(row["offering"])
            cafe_service_style = split_values(row["cafe_service_style"])
        except TaxonomyValidationError as error:
            row_issues.append(ValidationIssue(sample_id, "ERROR", "DUPLICATE_FACET_VALUE", str(error)))
            cuisine = cuisine_variant = dining_format = signature_menu = primary_beverage = offering = cafe_service_style = []

        all_values = cuisine + cuisine_variant + dining_format + signature_menu + primary_beverage + offering + cafe_service_style
        legacy_values = sorted(set(all_values) & LEGACY_CODES)
        if legacy_values:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "LEGACY_SUBTYPE",
                    f"legacy subtype codes are not canonical facets: {', '.join(legacy_values)}",
                )
            )

        _validate_vocab(row_issues, sample_id, "cuisine", cuisine, RESTAURANT_CUISINES)
        _validate_vocab(row_issues, sample_id, "dining_format", dining_format, RESTAURANT_DINING_FORMATS)
        _validate_vocab(row_issues, sample_id, "primary_beverage", primary_beverage, CAFE_PRIMARY_BEVERAGES)
        _validate_vocab(row_issues, sample_id, "offering", offering, CAFE_OFFERINGS)
        _validate_vocab(
            row_issues, sample_id, "cafe_service_style", cafe_service_style, CAFE_SERVICE_STYLES
        )

        if len(primary_beverage) > 1:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "MULTIPLE_PRIMARY_BEVERAGES",
                    "primary_beverage accepts zero or one value",
                )
            )

        if len(cuisine) > 1 or len(cuisine_variant) > 1 or len(dining_format) > 1:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "MULTIPLE_SINGLE_VALUE_FACET",
                    "cuisine, cuisine_variant, and dining_format accept zero or one value",
                )
            )
        if len(signature_menu) > 3:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "TOO_MANY_SIGNATURE_MENUS",
                    "signature_menu accepts at most three values",
                )
            )
        if cuisine_variant:
            allowed_variants = CUISINE_VARIANTS.get(cuisine[0] if cuisine else "", set())
            _validate_vocab(row_issues, sample_id, "cuisine_variant", cuisine_variant, allowed_variants)

        restaurant_facets = cuisine + cuisine_variant + dining_format + signature_menu
        cafe_facets = primary_beverage + offering + cafe_service_style
        if primary != "CAFE" and cafe_facets:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "CAFE_FACET_ON_RESTAURANT",
                    "CAFE facets require CAFE as primary taxonomy",
                )
            )
        if primary != "RESTAURANT" and restaurant_facets:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "ERROR",
                    "RESTAURANT_FACET_ON_CAFE",
                    "RESTAURANT facets require RESTAURANT as primary taxonomy",
                )
            )
        if primary == "RESTAURANT" and not restaurant_facets:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "NO_CLASSIFICATION_FACET",
                    "record at least cuisine, dining_format, or signature_menu",
                )
            )
        if primary == "CAFE" and not cafe_facets:
            row_issues.append(
                ValidationIssue(
                    sample_id,
                    "WARNING",
                    "NO_CLASSIFICATION_FACET",
                    "record at least primary_beverage, offering, or cafe_service_style",
                )
            )

        issues.extend(row_issues)
        if any(issue.severity == "ERROR" for issue in row_issues):
            invalid += 1
        elif row_issues:
            needs_review += 1
        else:
            valid += 1

    return ValidationSummary(
        samples=len(samples),
        restaurant_samples=restaurant_samples,
        cafe_samples=cafe_samples,
        valid=valid,
        needs_review=needs_review,
        invalid=invalid,
        issues=issues,
    )


def _validate_vocab(
    issues: list[ValidationIssue], sample_id: str, field: str, values: list[str], allowed: set[str]
) -> None:
    invalid_values = sorted(set(values) - allowed)
    if invalid_values:
        issues.append(
            ValidationIssue(
                sample_id,
                "ERROR",
                "UNKNOWN_FACET_VALUE",
                f"{field} has unsupported value(s): {', '.join(invalid_values)}",
            )
        )


def write_tour_api_candidates(raw_root: Path, output: Path, *, sample_size: int = 100) -> int:
    """Export up to ``sample_size`` real TourAPI food records for manual review."""
    if sample_size < 1:
        raise ValueError("sample_size must be at least 1")

    candidates: list[dict[str, str]] = []
    seen_ids: set[str] = set()
    for page_path in sorted((raw_root / "39_음식점").rglob("page-*.json")):
        page = json.loads(page_path.read_text(encoding="utf-8"))
        items = page["response"]["body"].get("items", {}).get("item", [])
        if isinstance(items, dict):
            items = [items]
        for item in items:
            source_id = str(item.get("contentid", "")).strip()
            name = str(item.get("title", "")).strip()
            if not source_id or not name or source_id in seen_ids:
                continue
            seen_ids.add(source_id)
            candidates.append(
                {
                    "sample_id": f"TOUR_API-{source_id}",
                    "place_name": name,
                    "source_provider": "TOUR_API",
                    "source_place_id": source_id,
                    "source_url": "",
                    "source_category": str(item.get("lclsSystm3", "")).strip(),
                    "primary_taxonomy": "",
                    "cuisine": "",
                    "cuisine_variant": "",
                    "dining_format": "",
                    "signature_menu": "",
                    "primary_beverage": "",
                    "offering": "",
                    "cafe_service_style": "",
                    "evidence": "",
                    "review_status": "PENDING",
                    "notes": "",
                }
            )
            if len(candidates) == sample_size:
                break
        if len(candidates) == sample_size:
            break

    if len(candidates) < sample_size:
        raise TaxonomyValidationError(
            f"only {len(candidates)} usable food records found; collect more TourAPI 39 pages"
        )
    write_samples(output, candidates)
    return len(candidates)


def write_tour_api_proposals(raw_root: Path, output: Path, *, sample_size: int = 440) -> int:
    """Export deterministic, reviewable first-pass Canonical proposals.

    Provider categories only seed a proposal. Ambiguous provider buckets deliberately
    stay ``NEEDS_MANUAL`` rather than being forced into FUSION_OTHER or UNCLASSIFIED.
    """
    if sample_size < 1:
        raise ValueError("sample_size must be at least 1")

    candidates: list[dict[str, str]] = []
    seen_ids: set[str] = set()
    for page_path in sorted((raw_root / "39_음식점").rglob("page-*.json")):
        page = json.loads(page_path.read_text(encoding="utf-8"))
        items = page["response"]["body"].get("items", {}).get("item", [])
        if isinstance(items, dict):
            items = [items]
        for item in items:
            source_id = str(item.get("contentid", "")).strip()
            name = str(item.get("title", "")).strip()
            if not source_id or not name or source_id in seen_ids:
                continue
            seen_ids.add(source_id)
            source_category = str(item.get("lclsSystm3", "")).strip() or page_path.parent.name
            proposal = _tour_api_proposal(source_category)
            candidates.append(
                {
                    "sample_id": f"TOUR_API-{source_id}",
                    "place_name": name,
                    "source_provider": "TOUR_API",
                    "source_place_id": source_id,
                    "source_url": "",
                    "source_category": source_category,
                    "primary_taxonomy": proposal["primary_taxonomy"],
                    "cuisine": proposal["cuisine"],
                    "cuisine_variant": proposal["cuisine_variant"],
                    "dining_format": "",
                    "signature_menu": "",
                    "primary_beverage": proposal["primary_beverage"],
                    "offering": proposal["offering"],
                    "cafe_service_style": "",
                    "evidence": proposal["evidence"],
                    "review_status": proposal["review_status"],
                    "notes": proposal["notes"],
                }
            )
            if len(candidates) == sample_size:
                write_samples(output, candidates)
                return len(candidates)

    if len(candidates) < sample_size:
        raise TaxonomyValidationError(
            f"only {len(candidates)} usable food records found; collect more TourAPI 39 pages"
        )
    write_samples(output, candidates)
    return len(candidates)


def _tour_api_proposal(source_category: str) -> dict[str, str]:
    direct_restaurant_cuisines = {
        "FD010100": "KOREAN",
        "FD020100": "CHINESE",
        "FD020200": "JAPANESE",
        "FD020300": "WESTERN",
        "FD030200": "WESTERN",
        "FD030400": "KOREAN",
    }
    if source_category in direct_restaurant_cuisines:
        return _proposal(
            primary_taxonomy="RESTAURANT",
            cuisine=direct_restaurant_cuisines[source_category],
            evidence=f"TourAPI provider category={source_category}; deterministic first-pass mapping",
            review_status="AUTO_PROPOSED",
        )
    if source_category == "FD050100":
        return _proposal(
            primary_taxonomy="CAFE",
            offering="COFFEE",
            evidence="TourAPI cafe provider category=FD050100; beverage identity still needs review",
            review_status="AUTO_PROPOSED",
        )
    if source_category == "FD050200":
        return _proposal(
            primary_taxonomy="CAFE",
            primary_beverage="TEA",
            offering="TEA",
            evidence="TourAPI traditional-tea provider category=FD050200",
            review_status="AUTO_PROPOSED",
        )
    if source_category == "FD030100":
        return _proposal(
            primary_taxonomy="SHOPPING",
            evidence="TourAPI bakery provider category=FD030100; visit purpose requires manual review",
            review_status="NEEDS_MANUAL",
            notes="CAFE secondary is possible only when on-site beverage and stay purpose are evidenced.",
        )
    if source_category == "FD020400":
        return _proposal(
            primary_taxonomy="RESTAURANT",
            evidence="TourAPI provider category=FD020400 is too broad to infer Cuisine",
            review_status="NEEDS_MANUAL",
        )
    return _proposal(
        primary_taxonomy="RESTAURANT",
        evidence=f"TourAPI provider category={source_category} has no safe Canonical mapping",
        review_status="NEEDS_MANUAL",
        notes="Do not substitute FUSION_OTHER or UNCLASSIFIED without external evidence.",
    )


def _proposal(
    *,
    primary_taxonomy: str,
    evidence: str,
    review_status: str,
    cuisine: str = "",
    cuisine_variant: str = "",
    primary_beverage: str = "",
    offering: str = "",
    notes: str = "",
) -> dict[str, str]:
    return {
        "primary_taxonomy": primary_taxonomy,
        "cuisine": cuisine,
        "cuisine_variant": cuisine_variant,
        "primary_beverage": primary_beverage,
        "offering": offering,
        "evidence": evidence,
        "review_status": review_status,
        "notes": notes,
    }


def write_samples(path: Path, samples: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=sorted(REQUIRED_COLUMNS))
        writer.writeheader()
        writer.writerows(samples)


def write_report(path: Path, summary: ValidationSummary) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(summary.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
