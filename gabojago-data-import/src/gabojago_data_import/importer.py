from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .config import Settings
from .mapping import load_subtype_mappings, resolve_subtype
from .repository import PlaceRepository
from .tour_api import CONTENT_TYPE_IDS, TourApiClient


@dataclass
class ImportStats:
    areas: int = 0
    sigungu: int = 0
    places: int = 0


class PlaceImporter:
    def __init__(self, settings: Settings, mapping_path: Path) -> None:
        self._settings = settings
        self._mapping_path = mapping_path

    def run(self, max_pages: int | None = None, area_codes: tuple[str, ...] | None = None) -> ImportStats:
        client = TourApiClient(self._settings)
        mappings = load_subtype_mappings(self._mapping_path)
        selected_codes = area_codes if area_codes is not None else self._settings.area_codes
        stats = ImportStats()

        with PlaceRepository(self._settings) as repository:
            areas = client.list_areas()
            if selected_codes:
                areas = [area for area in areas if area.code in selected_codes]
            for area in areas:
                area_id = repository.upsert_area(area)
                stats.areas += 1
                sigungu_list = client.list_sigungu(area.code)
                if not sigungu_list:
                    self._import_scope(
                        client, repository, mappings, area.code, None, area_id, max_pages, stats
                    )
                    continue
                for sigungu in sigungu_list:
                    region_id = repository.upsert_sigungu(area_id, sigungu, area.name)
                    stats.sigungu += 1
                    self._import_scope(
                        client, repository, mappings, area.code, sigungu.code, region_id, max_pages, stats
                    )
                    repository.commit_batch()
        return stats

    def _import_scope(
        self,
        client: TourApiClient,
        repository: PlaceRepository,
        mappings: dict,
        area_code: str,
        sigungu_code: str | None,
        region_id: int,
        max_pages: int | None,
        stats: ImportStats,
    ) -> None:
        for content_type_id in CONTENT_TYPE_IDS:
            for record in client.iter_places(area_code, sigungu_code, content_type_id, max_pages):
                repository.upsert_place(region_id, record, resolve_subtype(record, mappings))
                stats.places += 1
