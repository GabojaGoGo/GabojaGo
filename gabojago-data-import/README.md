# GabojaGo Place Data Import

TourAPI 장소 데이터를 GabojaGo MySQL의 `regions`, `places`, `categories`,
`place_categories`에 초기 적재하는 독립 Python 프로젝트입니다.

## 계약

- 백엔드가 JPA로 테이블을 생성한 뒤 실행합니다.
- `places`는 `(source_type, source_place_id)` 기준으로 upsert합니다.
- 적재기는 `assignment_type=IMPORTED`인 SUBTYPE 연결만 교체합니다.
- `DERIVED`, `MANUAL` 연결과 `place_attributes`는 보존합니다.
- 모든 지역을 적재하려면 `TOUR_AREA_CODES`를 비웁니다. 개발 초기에는 `6,36`처럼 범위를 제한합니다.

## 실행

```bash
cd ../gabojago-data-import
cp .env.example .env
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
gabojago-place-import --max-pages 2
```

`--max-pages`는 타입·시군구별 페이지 수를 제한합니다. 전체 초기 적재는 제한 없이 실행합니다.

```bash
gabojago-place-import
```

## 분류

`data/subtype_mappings.csv`에 TourAPI `cat3` 코드별 SUBTYPE을 정의합니다. 미등록 코드는
`TOUR_API_<cat3>` 코드와 원본 `cat3` 이름으로 보존하므로 원본 분류를 잃지 않습니다.
