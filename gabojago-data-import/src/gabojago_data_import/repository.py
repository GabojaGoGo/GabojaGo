from __future__ import annotations

from contextlib import AbstractContextManager
from typing import Any

import pymysql
from pymysql.cursors import Cursor

from .config import Settings
from .models import Area, PlaceRecord, Sigungu, Subtype


class PlaceRepository(AbstractContextManager["PlaceRepository"]):
    def __init__(self, settings: Settings) -> None:
        self._connection = pymysql.connect(
            host=settings.db_host,
            port=settings.db_port,
            user=settings.db_username,
            password=settings.db_password,
            database=settings.db_name,
            charset="utf8mb4",
            autocommit=False,
        )

    def __enter__(self) -> "PlaceRepository":
        self._assert_schema()
        return self

    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> None:
        if exc_type is None:
            self._connection.commit()
        else:
            self._connection.rollback()
        self._connection.close()

    def upsert_area(self, area: Area) -> int:
        region_key = f"TOUR:{area.code}"
        return self._upsert_region(
            region_key=region_key,
            parent_id=None,
            level="AREA",
            name=area.name,
            area_code=area.code,
            sigungu_code=None,
        )

    def upsert_sigungu(self, parent_id: int, sigungu: Sigungu, area_name: str) -> int:
        return self._upsert_region(
            region_key=f"TOUR:{sigungu.area_code}:{sigungu.code}",
            parent_id=parent_id,
            level="SIGUNGU",
            name=f"{area_name} {sigungu.name}",
            area_code=sigungu.area_code,
            sigungu_code=sigungu.code,
        )

    def upsert_place(self, region_id: int, record: PlaceRecord, subtype: Subtype | None) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO places (
                    region_id, place_type, name, address, latitude, longitude, phone,
                    image_url, thumbnail_url, source_type, source_place_id, created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'TOUR_API', %s, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                    region_id = VALUES(region_id),
                    place_type = VALUES(place_type),
                    name = VALUES(name),
                    address = VALUES(address),
                    latitude = VALUES(latitude),
                    longitude = VALUES(longitude),
                    phone = VALUES(phone),
                    image_url = VALUES(image_url),
                    thumbnail_url = VALUES(thumbnail_url),
                    updated_at = NOW(6)
                """,
                (
                    region_id,
                    record.place_type,
                    record.name,
                    record.address,
                    record.latitude,
                    record.longitude,
                    record.phone,
                    record.image_url,
                    record.thumbnail_url,
                    record.source_place_id,
                ),
            )
            place_id = self._select_id(
                cursor,
                "SELECT id FROM places WHERE source_type = 'TOUR_API' AND source_place_id = %s",
                (record.source_place_id,),
            )
            cursor.execute(
                "DELETE FROM place_categories WHERE place_id = %s AND assignment_type = 'IMPORTED'",
                (place_id,),
            )
            if subtype is None:
                return
            category_id = self._upsert_category(cursor, record.place_type, subtype)
            cursor.execute(
                """
                INSERT INTO place_categories (
                    place_id, category_id, status, assignment_type, created_at, updated_at
                ) VALUES (%s, %s, 'INCLUDED', 'IMPORTED', NOW(6), NOW(6))
                """,
                (place_id, category_id),
            )

    def commit_batch(self) -> None:
        self._connection.commit()

    def _assert_schema(self) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('regions', 'places', 'categories', 'place_categories')
                """
            )
            if cursor.fetchone()[0] != 4:
                raise RuntimeError(
                    "Place schema is missing. Start the Spring Boot backend once before importing data."
                )

    def _upsert_region(
        self,
        region_key: str,
        parent_id: int | None,
        level: str,
        name: str,
        area_code: str,
        sigungu_code: str | None,
    ) -> int:
        with self._connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO regions (
                    parent_id, region_key, level, name, tour_area_code, tour_sigungu_code,
                    datalab_code, active, created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, NULL, 1, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                    parent_id = VALUES(parent_id),
                    level = VALUES(level),
                    name = VALUES(name),
                    tour_area_code = VALUES(tour_area_code),
                    tour_sigungu_code = VALUES(tour_sigungu_code),
                    active = 1,
                    updated_at = NOW(6)
                """,
                (parent_id, region_key, level, name, area_code, sigungu_code),
            )
            return self._select_id(cursor, "SELECT id FROM regions WHERE region_key = %s", (region_key,))

    def _upsert_category(self, cursor: Cursor, place_type: str, subtype: Subtype) -> int:
        cursor.execute(
            """
            INSERT INTO categories (
                place_type, kind, code, name, description, is_active, created_at, updated_at
            ) VALUES (%s, 'SUBTYPE', %s, %s, NULL, 1, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE name = VALUES(name), is_active = 1, updated_at = NOW(6)
            """,
            (place_type, subtype.code, subtype.name),
        )
        return self._select_id(
            cursor,
            "SELECT id FROM categories WHERE place_type = %s AND kind = 'SUBTYPE' AND code = %s",
            (place_type, subtype.code),
        )

    @staticmethod
    def _select_id(cursor: Cursor, query: str, params: tuple[Any, ...]) -> int:
        cursor.execute(query, params)
        row = cursor.fetchone()
        if row is None:
            raise RuntimeError(f"Expected row was not found: {query}")
        return int(row[0])
