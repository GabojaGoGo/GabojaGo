from __future__ import annotations

import argparse
import csv
import os
import tempfile
from pathlib import Path

from dotenv import load_dotenv

from data_import.tour_api.importer import TourApiImportError, connect_database
from data_import.transit import MetroImportError, database_config

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DEFAULT_OUTPUT = PROJECT_ROOT / "mappings/busan_metro_access_points.csv"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="현재 MySQL의 부산 도시철도 출입구 데이터를 재사용 가능한 CSV로 내보냅니다.",
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser


def export_access_points(connection, output: Path) -> int:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT station_code, COALESCE(exit_number, '') AS exit_number,
                   latitude, longitude, osm_type, osm_id
            FROM metro_station_access_points
            ORDER BY station_code, exit_number, osm_type, osm_id
            """
        )
        rows = cursor.fetchall()
    if not rows:
        raise MetroImportError("metro_station_access_points에 내보낼 데이터가 없습니다.")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="",
        dir=output.parent,
        delete=False,
    ) as temporary:
        writer = csv.DictWriter(
            temporary,
            fieldnames=["station_code", "exit_number", "latitude", "longitude", "osm_type", "osm_id"],
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(rows)
        temporary_path = Path(temporary.name)
    temporary_path.replace(output)
    return len(rows)


def main() -> int:
    args = build_parser().parse_args()
    load_dotenv(PROJECT_ROOT / ".env")
    connection = None
    try:
        connection = connect_database(database_config(os.environ))
        count = export_access_points(connection, args.output)
    except (MetroImportError, TourApiImportError, OSError) as error:
        print(f"ERROR: {error}")
        return 1
    finally:
        if connection is not None:
            connection.close()

    print(f"출입구 {count}건을 내보냈습니다: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
