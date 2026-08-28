from collect_tour_api import build_parser as build_collect_parser
from collect_tour_api import load_environment
from import_tour_api_to_mysql import build_parser as build_import_parser


def test_collect_script_has_expected_options() -> None:
    help_text = build_collect_parser().format_help()

    assert "--content-type" in help_text
    assert "--max-pages" in help_text


def test_import_script_is_dry_run_by_default() -> None:
    args = build_import_parser().parse_args([])

    assert args.write is False


def test_load_environment_reads_simple_dotenv_without_dependency(tmp_path, monkeypatch) -> None:
    monkeypatch.chdir(tmp_path)
    (tmp_path / ".env").write_text("TOUR_API_SERVICE_KEY=test-key\n", encoding="utf-8")
    monkeypatch.delenv("TOUR_API_SERVICE_KEY", raising=False)

    load_environment()

    assert __import__("os").environ["TOUR_API_SERVICE_KEY"] == "test-key"
