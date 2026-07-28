from __future__ import annotations

from dataclasses import dataclass
import os

from dotenv import load_dotenv


@dataclass(frozen=True)
class Settings:
    tour_api_service_key: str
    tour_api_base_url: str
    db_host: str
    db_port: int
    db_name: str
    db_username: str
    db_password: str
    area_codes: tuple[str, ...]

    @classmethod
    def from_env(cls) -> "Settings":
        load_dotenv()
        service_key = os.getenv("TOUR_API_SERVICE_KEY", "").strip()
        if not service_key:
            raise ValueError("TOUR_API_SERVICE_KEY is required")

        area_codes = tuple(
            code.strip() for code in os.getenv("TOUR_AREA_CODES", "").split(",") if code.strip()
        )
        return cls(
            tour_api_service_key=service_key,
            tour_api_base_url=os.getenv(
                "TOUR_API_BASE_URL", "https://apis.data.go.kr/B551011/KorService1"
            ).rstrip("/"),
            db_host=os.getenv("DB_HOST", "127.0.0.1"),
            db_port=int(os.getenv("DB_PORT", "3306")),
            db_name=os.getenv("DB_NAME", "gabojago"),
            db_username=os.getenv("DB_USERNAME", "root"),
            db_password=os.getenv("DB_PASSWORD", "root"),
            area_codes=area_codes,
        )
