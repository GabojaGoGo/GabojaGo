from __future__ import annotations

import argparse
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from dotenv import load_dotenv

from data_import.transit import (
    MetroImportError,
    database_config,
    run_imports,
    selected_sources,
    source_manifest,
)

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DEFAULT_MAPPING_DIR = PROJECT_ROOT / "mappings"
DEFAULT_REPORTS = PROJECT_ROOT / "data/reports"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="부산 도시철도 그래프·좌표·출입구·시간표를 검증하거나 MySQL에 적재합니다.",
    )
    parser.add_argument("--write", action="store_true", help="검증 후 DB 데이터를 전체 교체합니다.")
    parser.add_argument(
        "--without-access-points",
        action="store_true",
        help="OSM 출입구 CSV 없이 그래프·좌표·시간표만 처리합니다.",
    )
    parser.add_argument("--mapping-dir", type=Path, default=DEFAULT_MAPPING_DIR)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    return parser


def write_report(reports: Path, *, write: bool, sources: tuple) -> Path:
    reports.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%SZ")
    path = reports / f"busan-metro-import-{timestamp}.json"
    path.write_text(
        json.dumps(
            {
                "mode": "replace" if write else "validate",
                "sources": source_manifest(sources),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return path


def main() -> int:
    args = build_parser().parse_args()
    load_dotenv(PROJECT_ROOT / ".env")
    try:
        sources = selected_sources(
            args.mapping_dir,
            require_access_points=not args.without_access_points,
        )
        if not any(source.name == "OSM 출입구" for source in sources):
            print("WARNING: 출입구 CSV 없이 적재합니다. 경로 안내의 출구 번호는 표시되지 않습니다.")
        run_imports(
            PROJECT_ROOT.parent / "gabojago-backend",
            sources,
            database_config(os.environ),
            write=args.write,
        )
        report_path = write_report(args.reports, write=args.write, sources=sources)
    except (MetroImportError, subprocess.CalledProcessError, OSError) as error:
        print(f"ERROR: {error}")
        return 1

    print(f"완료 ({'실제 저장' if args.write else '검증만 수행'}): {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
