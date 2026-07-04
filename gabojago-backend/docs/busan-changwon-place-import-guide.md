# 부산·창원 MVP 장소 적재 가이드

## 목적

TourAPI를 추천 때마다 실시간 호출하지 않고, 부산과 창원의 기본 장소를 우리 DB에 먼저 저장한다.
추천 알고리즘은 이후 `places`를 조회하고, 사람의 판단이 필요한 데이터는 별도의 큐레이션 단계에서 보완한다.

## 저장 구조

### `regions`

지역 계층과 TourAPI 지역 코드를 저장한다.

| 저장 지역 | level | TourAPI areaCode | TourAPI sigunguCode |
|---|---|---:|---:|
| 부산광역시 | AREA | 6 | - |
| 경상남도 | AREA | 36 | - |
| 창원시 | SIGUNGU | 36 | 16 |

창원시는 경상남도를 부모 지역으로 참조한다. 장소는 지역마다 별도 테이블에 저장하지 않고 `places.region_id`로 구분한다.

### `places`

추천 후보가 되는 장소의 기본 정보를 저장한다.

- 이름, 주소, 우편번호
- 위도, 경도
- 전화번호, 대표 이미지
- TourAPI ID와 원본 대·중·소 분류 코드
- 장소 대표 유형
- 운영 상태와 큐레이션 상태
- 사람이 입력할 평균 체류시간, 가격대, 영업시간

TourAPI 재수집은 이름·주소·좌표·이미지·분류 같은 기본 정보만 갱신한다. 사람이 입력한 평균 체류시간, 가격대, 영업시간은 덮어쓰지 않는다.

TourAPI 출처 정보도 `places`에 함께 저장한다.

- `source_type`: `TOUR_API`
- `source_place_id`: TourAPI의 `contentid`
- `source_category_large/medium/small`: 원본 분류 코드
- `source_modified_at`: TourAPI의 최종 수정 시각
- `last_synced_at`: 우리 서버가 마지막으로 확인한 시각

`source_type + source_place_id`가 고유키다. 원본 JSON 전체는 DB에 저장하지 않는다.
같은 적재 요청을 다시 실행해도 장소가 중복 생성되지 않고 기존 행이 갱신된다.

## MVP 적재 수량

부산과 창원에 각각 다음 70개를 목표로 적재한다.

| 장소 유형 | TourAPI contentTypeId | 지역당 목표 |
|---|---:|---:|
| 음식점 | 39 | 20 |
| 카페·디저트 | 39 (`A05020900`) | 8 |
| 관광지 | 12 | 15 |
| 문화시설 | 14 | 8 |
| 레포츠 | 28 | 8 |
| 숙박 | 32 | 7 |
| 쇼핑 | 38 | 4 |
| 합계 |  | 70 |

TourAPI 결과가 목표보다 적거나 좌표가 없는 항목이 많으면 실제 적재 수는 70개보다 적을 수 있다.

음식점은 TourAPI 소분류별로 순환 선택해 한식에 몰리지 않게 하고, `A05020900`은 카페·디저트로 분리한다.
술집과 주차장은 별도 데이터 원천과 수작업 검증이 필요하므로 이번 자동 적재 대상에는 포함하지 않는다.

## 실행 방법

서버를 실행한 뒤 다음 API를 한 번 호출한다.

```http
POST /api/place-admin/import/busan-changwon
```

예시:

```bash
curl -X POST \
  http://localhost:8080/api/place-admin/import/busan-changwon
```

현재 개발 편의를 위해 `/api/place-admin/**`는 인증 없이 열려 있다.
외부에 공개된 상태로 두면 누구나 TourAPI 호출과 DB 변경을 발생시킬 수 있으므로 운영 배포 전에는 관리자 권한으로 제한한다.

응답의 주요 값은 다음과 같다.

- `created`: 새로 생성된 장소 수
- `updated`: 기존 장소를 다시 확인하고 갱신한 수
- `skipped`: 이름, `contentid`, 좌표가 없어 제외된 수
- `failedRequests`: TourAPI 요청 자체가 실패한 횟수
- `savedByType`: 장소 유형별 실제 저장 수

## 다음 단계

1. 적재된 장소의 위치와 카테고리를 검수한다.
2. 추천에 사용하지 않을 장소는 `curation_status=EXCLUDED`로 바꾼다.
3. 사용할 장소는 `REVIEWED`로 바꾸고 평균 체류시간, 가격대, 영업시간을 입력한다.
4. 분위기, 사진 적합도, 주차 편의 같은 값은 이후 `place_attributes`에 저장한다.
5. 검수된 `REVIEWED` 장소만 추천 알고리즘 후보로 사용한다.
