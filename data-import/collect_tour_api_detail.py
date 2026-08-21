from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict
from datetime import UTC, datetime
from pathlib import Path

from dotenv import load_dotenv

from data_import.tour_api.collector import CONTENT_TYPE_DIRECTORIES, TourApiError
from data_import.tour_api.detail_collector import (
    REGION_CODES,
    TourApiDetailCollector,
    load_place_refs,
    stratified_sample,
)

DEFAULT_LIST_ROOT = Path("data/raw/tourism/tour-api")
DEFAULT_OUTPUT = Path("data/raw/tourism/tour-api-detail")
DEFAULT_REPORTS = Path("data/reports")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "이미 수집한 목록 조회 원본에서 부울경 대상을 뽑아 detailIntro2를 표본 수집합니다. "
            "대상 선정은 로컬 파일만 읽으며 API를 호출하지 않습니다."
        ),
    )
    parser.add_argument(
        "--content-type",
        default="12",
        choices=CONTENT_TYPE_DIRECTORIES,
        help="상세를 받을 유형. 기본값은 관광지(12)입니다.",
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        default=200,
        help="표본 크기. 모집단보다 크면 전량을 받습니다.",
    )
    parser.add_argument("--seed", type=int, default=20260820)
    parser.add_argument("--request-interval", type=float, default=0.2)
    parser.add_argument("--no-resume", action="store_true")
    parser.add_argument("--list-root", type=Path, default=DEFAULT_LIST_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="표본만 뽑고 API를 호출하지 않습니다. 대상 건수와 층 수를 먼저 확인할 때 씁니다.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    load_dotenv()

    if args.sample_size < 1:
        print("ERROR: --sample-size는 1 이상이어야 합니다.")
        return 2

    try:
        refs = load_place_refs(args.list_root, args.content_type)
    except (TourApiError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}")
        return 1

    if not refs:
        print(
            f"ERROR: {args.content_type} 유형에 부울경({'·'.join(REGION_CODES.values())}) "
            "대상이 없습니다."
        )
        return 1

    sample = stratified_sample(refs, args.sample_size, seed=args.seed)
    strata = len({ref.stratum for ref in sample})
    directory_name = CONTENT_TYPE_DIRECTORIES[args.content_type]

    print(f"[{args.content_type}] {directory_name}")
    print(f"  부울경 모집단 {len(refs):,}건")
    print(f"  표본 {len(sample):,}건 / {strata}개 층 (seed {args.seed})")

    if args.dry_run:
        print("\ndry-run이라 API를 호출하지 않았습니다.")
        return 0

    service_key = os.getenv("TOUR_API_SERVICE_KEY", "").strip()
    if not service_key:
        print("ERROR: TOUR_API_SERVICE_KEY가 .env에 설정되지 않았습니다.")
        return 2

    collector = TourApiDetailCollector(
        service_key=service_key,
        output_root=args.output,
        request_interval=args.request_interval,
    )

    try:
        summary = collector.collect(
            sample,
            content_type_id=args.content_type,
            population=len(refs),
            resume=not args.no_resume,
            on_progress=lambda index, total, ref, state: print(
                f"  {index}/{total} {state} {ref.content_id} {ref.title[:24]}"
            ),
        )
    except OSError as error:
        print(f"ERROR: {error}")
        return 1

    print(
        f"\n완료: 다운로드 {summary.fetched}건, 재사용 {summary.reused}건, "
        f"원천없음 {summary.empty}건, 실패 {summary.failed}건"
    )
    if summary.empty:
        print(
            "  원천없음은 TourAPI가 정상 응답(0000)에 빈 items를 준 건이다. "
            "수집 실패가 아니라 그 장소에 상세가 등록돼 있지 않다는 뜻이다."
        )

    rates = summary.fill_rates()
    if rates:
        print(
            "\n필드 채움률 "
            f"(분모: 상세가 존재한 {summary.fetched + summary.reused - summary.empty}건)"
        )
        for name, rate in rates.items():
            print(f"  {rate:5.1f}%  {name}")

    if summary.failures:
        print(f"\n실패 {len(summary.failures)}건 (앞 5건)")
        for line in summary.failures[:5]:
            print(f"  {line}")

    report_path = write_report(args.reports, args.content_type, summary)
    print(f"\n보고서: {report_path}")
    return 0


def write_report(reports: Path, content_type: str, summary) -> Path:
    reports.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    report_path = reports / f"tour-api-detail-{content_type}-{timestamp}.json"
    report = asdict(summary)
    report["output_dir"] = str(summary.output_dir)
    report["field_fill_rates"] = {
        name: round(rate, 1) for name, rate in summary.fill_rates().items()
    }
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return report_path


if __name__ == "__main__":
    raise SystemExit(main())
