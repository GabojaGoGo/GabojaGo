import pytest

from data_import.transit import (
    MetroImportError,
    MetroSource,
    boot_run_command,
    database_config,
    selected_sources,
)


def test_boot_run_command_uses_validate_mode_and_absolute_path(tmp_path) -> None:
    source = MetroSource("정적 그래프", "transit.static-import", tmp_path / "network source.csv")

    command = boot_run_command(source, write=False)

    assert command[:2] == ["./gradlew", "bootRun"]
    assert "--spring.main.web-application-type=none" in command[2]
    assert "--transit.static-import.mode=validate" in command[2]
    assert f"'--transit.static-import.path={source.path.resolve()}'" in command[2]


def test_database_config_requires_password() -> None:
    with pytest.raises(MetroImportError, match="DB_PASSWORD"):
        database_config({"DB_USERNAME": "root", "DB_PASSWORD": ""})


def test_selected_sources_can_explicitly_skip_missing_access_points(tmp_path) -> None:
    for name in (
        "역간 거리 및 소요시간 정보.csv",
        "부산교통공사_도시철도역사정보_20210226.csv",
        "부산교통공사_부산도시철도 운행 정보_20260722.csv",
    ):
        (tmp_path / name).write_text("source", encoding="utf-8")

    sources = selected_sources(tmp_path, require_access_points=False)

    assert [source.name for source in sources] == ["정적 그래프", "공식 역 좌표", "열차 시간표"]
