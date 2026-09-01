"""Measure TourAPI restaurant free-text values for P2-D (GO-133)."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

from data_import.tour_api.food_field_analysis import analyze_detail_directory


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="TourAPI 음식점 detailIntro2 원본 자유텍스트 분포를 집계합니다 (P2-D 전용)."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("data/raw/tourism/tour-api-detail/39_음식점"),
        help="음식점(39) detailIntro2 JSON 디렉터리",
    )
    parser.add_argument("--reports", type=Path, default=Path("data/reports"))
    parser.add_argument(
        "--top-n",
        type=int,
        default=500,
        help="report에 남길 원문 빈도 수 (기본 500; 현재 P2-D 표본의 전체 분포를 보존)",
    )
    parser.add_argument("--output", type=Path, help="기본 타임스탬프 report 경로를 덮어씁니다")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not args.input.is_dir():
        print(f"ERROR: detailIntro2 입력 디렉터리가 없습니다: {args.input}")
        return 2

    report = analyze_detail_directory(args.input, top_n=args.top_n)
    if not report["input_files"]:
        print(f"ERROR: detailIntro2 JSON 파일이 없습니다: {args.input}")
        return 2

    output = args.output
    if output is None:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%SZ")
        output = args.reports / f"tour-api-food-field-analysis-{timestamp}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"report: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
