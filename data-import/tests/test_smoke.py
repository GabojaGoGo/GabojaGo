from collect_tour_api import build_parser as build_collect_parser
from import_tour_api_to_mysql import build_parser as build_import_parser


def test_collect_script_has_expected_options() -> None:
    help_text = build_collect_parser().format_help()

    assert "--content-type" in help_text
    assert "--max-pages" in help_text


def test_import_script_is_dry_run_by_default() -> None:
    args = build_import_parser().parse_args([])

    assert args.write is False
