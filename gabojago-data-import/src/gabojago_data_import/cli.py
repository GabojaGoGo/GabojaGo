from __future__ import annotations

import argparse
from pathlib import Path

from .config import Settings
from .importer import PlaceImporter


def main() -> None:
    parser = argparse.ArgumentParser(description="Import TourAPI places into GabojaGo MySQL")
    parser.add_argument("--max-pages", type=int, default=None, help="Maximum pages per content type and sigungu")
    parser.add_argument("--area-codes", default=None, help="Comma-separated TourAPI area codes overriding TOUR_AREA_CODES")
    args = parser.parse_args()

    settings = Settings.from_env()
    area_codes = (
        tuple(code.strip() for code in args.area_codes.split(",") if code.strip())
        if args.area_codes is not None
        else None
    )
    mapping_path = Path(__file__).resolve().parents[2] / "data" / "subtype_mappings.csv"
    stats = PlaceImporter(settings, mapping_path).run(max_pages=args.max_pages, area_codes=area_codes)
    print(f"Import complete: areas={stats.areas}, sigungu={stats.sigungu}, places={stats.places}")
