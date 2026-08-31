from __future__ import annotations

import csv
import json
from collections.abc import Iterable, Iterator
from dataclasses import asdict, dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import pymysql
from pymysql.connections import Connection

CONTENT_TYPE_TO_PLACE_TYPE = {
    "12": "TOURIST_SPOT",
    "14": "TOURIST_SPOT",
    "15": "TOURIST_SPOT",
    "28": "ACTIVITY",
    "32": "ACCOMMODATION",
    "38": "SHOP",
    "39": "RESTAURANT",
}
VALID_PLACE_TYPES = {
    "CAFE",
    "RESTAURANT",
    "BAR",
    "SHOP",
    "TOURIST_SPOT",
    "ACTIVITY",
    "ACCOMMODATION",
    "PARKING_LOT",
}


@dataclass(frozen=True)
class AreaDefinition:
    legal_code: str
    region_key: str
    name: str


LEGAL_AREA_DEFINITIONS = (
    AreaDefinition("11", "seoul", "서울특별시"),
    AreaDefinition("12", "jeonnam-gwangju", "전남광주통합특별시"),
    AreaDefinition("26", "busan", "부산광역시"),
    AreaDefinition("27", "daegu", "대구광역시"),
    AreaDefinition("28", "incheon", "인천광역시"),
    AreaDefinition("30", "daejeon", "대전광역시"),
    AreaDefinition("31", "ulsan", "울산광역시"),
    AreaDefinition("36110", "sejong", "세종특별자치시"),
    AreaDefinition("41", "gyeonggi", "경기도"),
    AreaDefinition("43", "chungbuk", "충청북도"),
    AreaDefinition("44", "chungnam", "충청남도"),
    AreaDefinition("47", "gyeongbuk", "경상북도"),
    AreaDefinition("48", "gyeongnam", "경상남도"),
    AreaDefinition("50", "jeju", "제주특별자치도"),
    AreaDefinition("51", "gangwon", "강원특별자치도"),
    AreaDefinition("52", "jeonbuk", "전북특별자치도"),
)


class TourApiImportError(RuntimeError):
    """Raised when raw data or database configuration is invalid."""


@dataclass(frozen=True)
class DatabaseConfig:
    host: str
    port: int
    database: str
    username: str
    password: str


@dataclass(frozen=True)
class RegionTarget:
    id: int
    region_key: str
    address_prefix: str


@dataclass(frozen=True)
class SubtypeMapping:
    source_content_type_id: str
    source_lcls3_code: str
    target_place_type: str
    target_subtype_code: str
    target_subtype_name: str
    mapping_status: str

    @property
    def place_category_status(self) -> str:
        return "INCLUDED" if self.mapping_status == "CONFIRMED" else "NEED_REVIEW"


@dataclass(frozen=True)
class PlaceRecord:
    region_id: int
    place_type: str
    name: str
    address: str | None
    latitude: Decimal
    longitude: Decimal
    phone: str | None
    image_url: str | None
    thumbnail_url: str | None
    source_place_id: str


@dataclass
class ImportSummary:
    scanned: int = 0
    eligible: int = 0
    created: int = 0
    updated: int = 0
    unresolved_region: int = 0
    unsupported: int = 0
    invalid: int = 0
    new_regions: int = 0
    subtype_links: int = 0
    multi_subtype_places: int = 0
    unmapped_subtype: int = 0

    def to_dict(self) -> dict[str, int]:
        return asdict(self)


def connect_database(config: DatabaseConfig) -> Connection:
    try:
        return pymysql.connect(
            host=config.host,
            port=config.port,
            user=config.username,
            password=config.password,
            database=config.database,
            charset="utf8mb4",
            autocommit=False,
            cursorclass=pymysql.cursors.DictCursor,
        )
    except pymysql.MySQLError as error:
        raise TourApiImportError(f"MySQL connection failed: {error}") from error


def load_subtype_mappings(
    path: Path,
) -> dict[tuple[str, str], tuple[SubtypeMapping, ...]]:
    if not path.is_file():
        raise TourApiImportError(f"Subtype mapping file not found: {path}")

    required_columns = {
        "source_content_type_id",
        "source_lcls3_code",
        "target_place_type",
        "target_subtype_code",
        "target_subtype_name",
        "mapping_status",
    }
    mappings: dict[tuple[str, str], list[SubtypeMapping]] = {}
    mapping_targets: set[tuple[str, str, str]] = set()
    try:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            missing_columns = required_columns - set(reader.fieldnames or [])
            if missing_columns:
                raise TourApiImportError(
                    f"Subtype mapping columns missing: {', '.join(sorted(missing_columns))}"
                )

            for line_number, row in enumerate(reader, start=2):
                mapping = SubtypeMapping(
                    source_content_type_id=required_csv_value(
                        row, "source_content_type_id", line_number
                    ),
                    source_lcls3_code=required_csv_value(row, "source_lcls3_code", line_number),
                    target_place_type=required_csv_value(row, "target_place_type", line_number),
                    target_subtype_code=required_csv_value(row, "target_subtype_code", line_number),
                    target_subtype_name=required_csv_value(row, "target_subtype_name", line_number),
                    mapping_status=required_csv_value(row, "mapping_status", line_number),
                )
                if mapping.target_place_type not in VALID_PLACE_TYPES:
                    raise TourApiImportError(
                        f"Invalid target_place_type at line {line_number}: "
                        f"{mapping.target_place_type}"
                    )
                if mapping.mapping_status not in {"CONFIRMED", "NEEDS_REVIEW"}:
                    raise TourApiImportError(
                        f"Invalid mapping_status at line {line_number}: {mapping.mapping_status}"
                    )
                if len(mapping.target_subtype_code) > 50:
                    raise TourApiImportError(
                        f"target_subtype_code is longer than 50 characters at line {line_number}"
                    )
                if len(mapping.target_subtype_name) > 100:
                    raise TourApiImportError(
                        f"target_subtype_name is longer than 100 characters at line {line_number}"
                    )
                key = (
                    mapping.source_content_type_id,
                    mapping.source_lcls3_code,
                )
                target_key = (*key, mapping.target_subtype_code)
                if target_key in mapping_targets:
                    raise TourApiImportError(
                        f"Duplicate subtype target at line {line_number}: {target_key}"
                    )
                existing_place_types = {
                    existing.target_place_type for existing in mappings.get(key, [])
                }
                if existing_place_types and mapping.target_place_type not in existing_place_types:
                    raise TourApiImportError(
                        f"Conflicting target_place_type at line {line_number}: {key}"
                    )
                mappings.setdefault(key, []).append(mapping)
                mapping_targets.add(target_key)
    except OSError as error:
        raise TourApiImportError(f"Cannot read subtype mapping file: {path}") from error

    if not mappings:
        raise TourApiImportError("Subtype mapping file is empty")
    return {key: tuple(values) for key, values in mappings.items()}


def required_csv_value(
    row: dict[str, str | None],
    column: str,
    line_number: int,
) -> str:
    value = clean_text(row.get(column))
    if value is None:
        raise TourApiImportError(f"Subtype mapping value missing at line {line_number}: {column}")
    return value


def prepare_regions(
    connection: Connection,
    *,
    dry_run: bool,
) -> tuple[dict[str, RegionTarget], list[RegionTarget], int]:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT r.id, r.region_key, r.name, r.level, parent.name AS parent_name
            FROM regions r
            LEFT JOIN regions parent ON parent.id = r.parent_id
            WHERE r.active = TRUE
            """
        )
        rows = cursor.fetchall()

    rows_by_key = {str(row["region_key"]): row for row in rows}
    area_by_code: dict[str, RegionTarget] = {}
    address_targets: list[RegionTarget] = []
    new_regions = 0

    for definition in LEGAL_AREA_DEFINITIONS:
        row = rows_by_key.get(definition.region_key)
        if row is None:
            new_regions += 1
            region_id = -new_regions
            if not dry_run:
                region_id = insert_area_region(connection, definition)
        else:
            region_id = int(row["id"])

        target = RegionTarget(
            id=region_id,
            region_key=definition.region_key,
            address_prefix=definition.name,
        )
        area_by_code[definition.legal_code] = target
        address_targets.append(target)

    for row in rows:
        if row["level"] != "SIGUNGU":
            continue
        parent_name = clean_text(row["parent_name"])
        if parent_name is None:
            continue
        address_targets.append(
            RegionTarget(
                id=int(row["id"]),
                region_key=str(row["region_key"]),
                address_prefix=f"{parent_name} {row['name']}",
            )
        )

    address_targets.sort(key=lambda target: len(target.address_prefix), reverse=True)
    return area_by_code, address_targets, new_regions


def insert_area_region(connection: Connection, definition: AreaDefinition) -> int:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            INSERT INTO regions (
                created_at, updated_at, active, level, name, region_key,
                tour_area_code, tour_sigungu_code, datalab_code, parent_id
            ) VALUES (
                NOW(6), NOW(6), TRUE, 'AREA', %s, %s, %s, NULL, NULL, NULL
            )
            """,
            (definition.name, definition.region_key, definition.legal_code),
        )
        return int(cursor.lastrowid)


def iter_tour_api_items(raw_root: Path) -> Iterator[dict[str, Any]]:
    if not raw_root.is_dir():
        raise TourApiImportError(f"Raw directory not found: {raw_root}")

    page_paths = sorted(raw_root.rglob("page-*.json"))
    if not page_paths:
        raise TourApiImportError(f"No TourAPI page files found under: {raw_root}")

    for page_path in page_paths:
        try:
            root = json.loads(page_path.read_text(encoding="utf-8"))
            items = root["response"]["body"].get("items") or {}
            item_data = items.get("item") if isinstance(items, dict) else None
        except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
            raise TourApiImportError(f"Invalid raw page: {page_path}") from error

        if isinstance(item_data, dict):
            yield item_data
        elif isinstance(item_data, list):
            yield from item_data


def resolve_region(
    item: dict[str, Any],
    area_by_code: dict[str, RegionTarget],
    address_targets: list[RegionTarget],
) -> RegionTarget | None:
    address = clean_text(item.get("addr1"))
    if address:
        normalized = " ".join(address.split())
        for target in address_targets:
            if normalized.startswith(target.address_prefix):
                return target

    legal_area_code = clean_text(item.get("lDongRegnCd"))
    if legal_area_code:
        return area_by_code.get(legal_area_code)
    return None


def to_place_record(
    item: dict[str, Any],
    region: RegionTarget,
    subtype_mappings: Iterable[SubtypeMapping] = (),
) -> PlaceRecord:
    content_type_id = clean_text(item.get("contenttypeid"))
    place_type = CONTENT_TYPE_TO_PLACE_TYPE.get(content_type_id or "")
    if place_type is None:
        raise ValueError(f"unsupported content type: {content_type_id}")

    content_id = required_text(item, "contentid")
    if len(content_id) > 64:
        raise ValueError("contentid is longer than 64 characters")
    title = required_text(item, "title")[:200]
    longitude = required_decimal(item, "mapx")
    latitude = required_decimal(item, "mapy")
    if longitude == 0 or latitude == 0:
        raise ValueError("coordinates must not be zero")
    if not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
        raise ValueError("coordinates are out of range")

    mapped_place_types = {mapping.target_place_type for mapping in subtype_mappings}
    if len(mapped_place_types) > 1:
        raise ValueError("subtype mappings contain multiple PlaceTypes")
    if mapped_place_types:
        place_type = mapped_place_types.pop()

    address_parts = [clean_text(item.get("addr1")), clean_text(item.get("addr2"))]
    address = limited_text(" ".join(part for part in address_parts if part), 500)
    return PlaceRecord(
        region_id=region.id,
        place_type=place_type,
        name=title,
        address=address,
        latitude=latitude.quantize(Decimal("0.0000001")),
        longitude=longitude.quantize(Decimal("0.0000001")),
        phone=limited_text(item.get("tel"), 100),
        image_url=limited_text(item.get("firstimage"), 1000),
        thumbnail_url=limited_text(item.get("firstimage2"), 1000),
        source_place_id=content_id,
    )


def import_tour_api(
    connection: Connection,
    raw_root: Path,
    mapping_path: Path,
    *,
    limit: int | None = None,
    dry_run: bool = True,
) -> ImportSummary:
    area_by_code, address_targets, new_regions = prepare_regions(
        connection,
        dry_run=dry_run,
    )
    subtype_mappings = load_subtype_mappings(mapping_path)
    category_ids = load_subtype_category_ids(
        connection,
        (mapping for mappings in subtype_mappings.values() for mapping in mappings),
    )
    existing_ids = load_existing_source_ids(connection)
    seen_ids: set[str] = set()
    summary = ImportSummary(new_regions=new_regions)

    try:
        for item in iter_tour_api_items(raw_root):
            summary.scanned += 1
            content_type_id = clean_text(item.get("contenttypeid"))
            if content_type_id not in CONTENT_TYPE_TO_PLACE_TYPE:
                summary.unsupported += 1
                continue

            region = resolve_region(item, area_by_code, address_targets)
            if region is None:
                summary.unresolved_region += 1
                continue

            mapping_key = (
                content_type_id,
                clean_text(item.get("lclsSystm3")) or "",
            )
            mappings = subtype_mappings.get(mapping_key, ())
            if not mappings:
                summary.unmapped_subtype += 1

            try:
                record = to_place_record(item, region, mappings)
            except ValueError:
                summary.invalid += 1
                continue

            if record.source_place_id in seen_ids:
                continue
            seen_ids.add(record.source_place_id)
            summary.eligible += 1

            if record.source_place_id in existing_ids:
                summary.updated += 1
            else:
                summary.created += 1
                existing_ids.add(record.source_place_id)

            summary.subtype_links += len(mappings)
            if len(mappings) > 1:
                summary.multi_subtype_places += 1

            if not dry_run:
                place_id = upsert_place(connection, record)
                subtype_links = [
                    (
                        category_ids[
                            (
                                mapping.target_place_type,
                                mapping.target_subtype_code,
                            )
                        ],
                        mapping.place_category_status,
                    )
                    for mapping in mappings
                ]
                replace_subtype_links(
                    connection,
                    place_id,
                    subtype_links,
                )
                if summary.eligible % 500 == 0:
                    connection.commit()

            if limit is not None and summary.eligible >= limit:
                break

        if dry_run:
            connection.rollback()
        else:
            connection.commit()
    except Exception:
        connection.rollback()
        raise

    return summary


def load_existing_source_ids(connection: Connection) -> set[str]:
    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT source_place_id FROM places "
            "WHERE source_type = 'TOUR_API' AND source_place_id IS NOT NULL"
        )
        return {str(row["source_place_id"]) for row in cursor.fetchall()}


def load_subtype_category_ids(
    connection: Connection,
    mappings: Iterable[SubtypeMapping],
) -> dict[tuple[str, str], int]:
    definitions: dict[tuple[str, str], str] = {}
    for mapping in mappings:
        key = (mapping.target_place_type, mapping.target_subtype_code)
        existing_name = definitions.get(key)
        if existing_name is not None and existing_name != mapping.target_subtype_name:
            raise TourApiImportError(
                f"Subtype name conflict for {mapping.target_place_type}/"
                f"{mapping.target_subtype_code}: "
                f"{existing_name} != {mapping.target_subtype_name}"
            )
        definitions[key] = mapping.target_subtype_name

    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT id, place_type, code, name "
            "FROM categories WHERE kind = 'SUBTYPE'"
        )
        existing = {(str(row["place_type"]), str(row["code"])): row for row in cursor.fetchall()}

    category_ids: dict[tuple[str, str], int] = {}
    for (place_type, code), name in sorted(definitions.items()):
        key = (place_type, code)
        row = existing.get(key)
        if row is None:
            raise TourApiImportError(
                f"Subtype Category is not defined by backend enum: {place_type}/{code}"
            )
        if str(row["name"]) != name:
            raise TourApiImportError(
                f"Subtype name differs from backend enum for {place_type}/{code}: "
                f"{name} != {row['name']}"
            )
        category_ids[key] = int(row["id"])

    return category_ids


def replace_subtype_links(
    connection: Connection,
    place_id: int,
    subtype_links: Iterable[tuple[int, str]],
) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            DELETE pc
            FROM place_categories pc
            JOIN categories c ON c.id = pc.category_id
            WHERE pc.place_id = %s
              AND c.kind = 'SUBTYPE'
            """,
            (place_id,),
        )
        cursor.executemany(
            """
            INSERT INTO place_categories (
                created_at, updated_at, status, category_id, place_id
            ) VALUES (
                NOW(6), NOW(6), %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                updated_at = updated_at
            """,
            [(status, category_id, place_id) for category_id, status in subtype_links],
        )


def upsert_place(connection: Connection, record: PlaceRecord) -> int:
    query = """
        INSERT INTO places (
            created_at, updated_at, region_id, place_type, name, address,
            latitude, longitude, phone, image_url, thumbnail_url,
            source_type, source_place_id
        ) VALUES (
            NOW(6), NOW(6), %(region_id)s, %(place_type)s, %(name)s, %(address)s,
            %(latitude)s, %(longitude)s, %(phone)s, %(image_url)s, %(thumbnail_url)s,
            'TOUR_API', %(source_place_id)s
        )
        ON DUPLICATE KEY UPDATE
            updated_at = NOW(6),
            region_id = VALUES(region_id),
            place_type = VALUES(place_type),
            name = VALUES(name),
            address = VALUES(address),
            latitude = VALUES(latitude),
            longitude = VALUES(longitude),
            phone = VALUES(phone),
            image_url = VALUES(image_url),
            thumbnail_url = VALUES(thumbnail_url),
            id = LAST_INSERT_ID(id)
    """
    try:
        with connection.cursor() as cursor:
            cursor.execute(query, asdict(record))
            return int(cursor.lastrowid)
    except pymysql.MySQLError as error:
        raise TourApiImportError(
            f"MySQL upsert failed for contentid={record.source_place_id}: {error}"
        ) from error


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def limited_text(value: Any, max_length: int) -> str | None:
    text = clean_text(value)
    if text is None:
        return None
    return text[:max_length]


def required_text(item: dict[str, Any], field: str) -> str:
    value = clean_text(item.get(field))
    if value is None:
        raise ValueError(f"missing {field}")
    return value


def required_decimal(item: dict[str, Any], field: str) -> Decimal:
    value = required_text(item, field)
    try:
        return Decimal(value)
    except InvalidOperation as error:
        raise ValueError(f"invalid {field}: {value}") from error
