"""부산 도시철도 정적 데이터를 백엔드 검증·적재기로 이관하는 보조 모듈."""

from __future__ import annotations

import hashlib
import os
import shlex
import subprocess
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path

from data_import.tour_api.importer import DatabaseConfig


class MetroImportError(RuntimeError):
    """Raised when the local metro import prerequisites are invalid."""


@dataclass(frozen=True)
class MetroSource:
    name: str
    property_prefix: str
    path: Path


def metro_sources(mapping_dir: Path) -> tuple[MetroSource, ...]:
    return (
        MetroSource(
            "정적 그래프",
            "transit.static-import",
            mapping_dir / "역간 거리 및 소요시간 정보.csv",
        ),
        MetroSource(
            "공식 역 좌표",
            "transit.station-coordinate-import",
            mapping_dir / "부산교통공사_도시철도역사정보_20210226.csv",
        ),
        MetroSource(
            "OSM 출입구",
            "transit.access-point-import",
            mapping_dir / "busan_metro_access_points.csv",
        ),
        MetroSource(
            "열차 시간표",
            "transit.timetable-import",
            mapping_dir / "부산교통공사_부산도시철도 운행 정보_20260722.csv",
        ),
    )


def database_config(environment: Mapping[str, str]) -> DatabaseConfig:
    username = environment.get("DB_USERNAME", "").strip()
    password = environment.get("DB_PASSWORD", "")
    if not username:
        raise MetroImportError("DB_USERNAME이 data-import/.env에 설정되지 않았습니다.")
    if not password:
        raise MetroImportError("DB_PASSWORD가 data-import/.env에 설정되지 않았습니다.")
    try:
        port = int(environment.get("IMPORT_DB_PORT", "3306"))
    except ValueError as error:
        raise MetroImportError("IMPORT_DB_PORT는 숫자여야 합니다.") from error
    if not 1 <= port <= 65535:
        raise MetroImportError("IMPORT_DB_PORT는 1부터 65535까지 사용할 수 있습니다.")
    return DatabaseConfig(
        host=environment.get("IMPORT_DB_HOST", "127.0.0.1").strip(),
        port=port,
        database=environment.get("IMPORT_DB_NAME", "gabojago").strip(),
        username=username,
        password=password,
    )


def backend_environment(config: DatabaseConfig) -> dict[str, str]:
    environment = os.environ.copy()
    environment.update(
        {
            "DB_URL": (
                f"jdbc:mysql://{config.host}:{config.port}/{config.database}"
                "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ),
            "DB_USERNAME": config.username,
            "DB_PASSWORD": config.password,
            # 적재 명령은 웹 서버를 열지 않지만, Spring context가 필요한 빈을 만들 수 있다.
            # 외부 API 호출은 수행하지 않으므로 실제 키를 전달하지 않는다.
            "JWT_SECRET": "transit-import-local-only-secret-32-bytes",
            "TOUR_API_SERVICE_KEY": "unused-for-transit-import",
            "KAKAO_REST_API_KEY": "unused-for-transit-import",
        }
    )
    return environment


def boot_run_command(source: MetroSource, *, write: bool) -> list[str]:
    mode = "replace" if write else "validate"
    source_path_argument = shlex.quote(
        f"--{source.property_prefix}.path={source.path.resolve()}"
    )
    arguments = " ".join(
        (
            "--spring.main.web-application-type=none",
            f"--{source.property_prefix}.enabled=true",
            f"--{source.property_prefix}.mode={mode}",
            source_path_argument,
        )
    )
    return ["./gradlew", "bootRun", f"--args={arguments}", "--no-daemon"]


def selected_sources(mapping_dir: Path, *, require_access_points: bool) -> tuple[MetroSource, ...]:
    sources = metro_sources(mapping_dir)
    missing_required = [source.name for source in sources if source.name != "OSM 출입구" and not source.path.is_file()]
    if missing_required:
        raise MetroImportError(f"필수 부산 도시철도 원본 파일이 없습니다: {', '.join(missing_required)}")

    access_source = next(source for source in sources if source.name == "OSM 출입구")
    if require_access_points and not access_source.path.is_file():
        raise MetroImportError(
            "출입구 CSV가 없습니다. 먼저 scripts/export_busan_metro_access_points.py를 실행하거나 "
            "--without-access-points 옵션을 사용하세요."
        )
    return tuple(source for source in sources if source.name != "OSM 출입구" or source.path.is_file())


def run_imports(
    backend_dir: Path,
    sources: tuple[MetroSource, ...],
    config: DatabaseConfig,
    *,
    write: bool,
    runner: Callable[..., object] = subprocess.run,
) -> None:
    for source in sources:
        print(f"[{source.name}] {'적재' if write else '검증'} 시작")
        runner(
            boot_run_command(source, write=write),
            cwd=backend_dir,
            env=backend_environment(config),
            check=True,
        )


def source_manifest(sources: tuple[MetroSource, ...]) -> list[dict[str, str]]:
    return [
        {
            "name": source.name,
            "path": str(source.path),
            "sha256": hashlib.sha256(source.path.read_bytes()).hexdigest(),
        }
        for source in sources
    ]
