# 장소 데이터 Postman 테스트

> MVP 데이터 검수 편의를 위해 `/api/place-admin/**`는 현재 인증 없이 열려 있다.
> 운영 배포 전에는 관리자 권한 또는 별도 관리망으로 반드시 제한한다.

## 1. 서버 실행

```bash
docker compose up -d --build
docker compose logs -f backend
```

서버 시작 시 JPA가 현재 엔티티 기준으로 테이블을 생성·갱신한다.

이전 구조의 테이블을 이미 만든 테스트 DB이고 보존할 데이터가 없다면 한 번만 초기화할 수 있다.

```bash
docker compose down -v
docker compose up -d --build
```

`down -v`는 MySQL 데이터를 모두 삭제하므로 필요한 데이터가 있을 때는 실행하지 않는다.

## 2. 부산·창원 자동 적재

```http
POST http://localhost:8080/api/place-admin/import/busan-changwon
```

지역당 FOOD 20, CAFE 8, ATTRACTION 15, CULTURE 8, ACTIVITY 8, LODGING 7,
SHOPPING 4를 목표로 저장한다. 같은 유형 안에서도 TourAPI 소분류를 순환해 한 분류에 몰리지 않게 선택한다.

## 3. 적재 목록 확인

부산의 검수 전 장소:

```http
GET http://localhost:8080/api/place-admin/places?regionKey=TOUR:6&curationStatus=IMPORTED&page=0&size=20
```

창원의 검수 전 장소:

```http
GET http://localhost:8080/api/place-admin/places?regionKey=TOUR:36:16&curationStatus=IMPORTED&page=0&size=20
```

필터 예시:

```http
GET http://localhost:8080/api/place-admin/places?regionKey=TOUR:6&primaryType=FOOD
```

응답에서 `source*` 필드는 자동 수집값이고 `category*`, `averageStayMinutes`, `priceLevel`은 수동 검수값이다.

## 4. 장소 상세 확인

```http
GET http://localhost:8080/api/place-admin/places/{placeId}
```

장소 기본정보, 영업시간, 속성, 적합도, 주차정보를 한 번에 반환한다.

## 5. 수동 검수

```http
PATCH http://localhost:8080/api/place-admin/places/{placeId}/review
Content-Type: application/json
```

```json
{
  "primaryType": "CAFE",
  "categoryLarge": "FOOD_BEVERAGE",
  "categoryMedium": "CAFE_DESSERT",
  "categorySmall": "DESSERT_CAFE",
  "averageStayMinutes": 90,
  "priceLevel": 2,
  "operatingHours": {
    "MON": [{"open": "10:00", "close": "22:00"}],
    "TUE": [{"open": "10:00", "close": "22:00"}]
  }
}
```

성공하면 `curationStatus`가 `REVIEWED`로 바뀐다.

## 6. 장소 속성 입력

```http
PUT http://localhost:8080/api/place-admin/places/{placeId}/attributes/PHOTO_SPOT
Content-Type: application/json
```

```json
{
  "score": 0.9,
  "confidence": 1.0,
  "evidence": {
    "note": "사진 촬영 공간과 대표 이미지 직접 검수"
  }
}
```

사용 가능한 MVP 코드는 다음과 같다.

```text
QUIET, PHOTO_SPOT, DESSERT_GOOD, PARKING_EASY,
VALUE_FOR_MONEY, TRANSIT_FRIENDLY, OUTDOOR, COZY, OCEAN_VIEW
```

## 7. 목적 적합도 입력

파생 배치 구현 전에는 테스트 목적으로 직접 넣을 수 있다.

```http
PUT http://localhost:8080/api/place-admin/places/{placeId}/suitabilities/INTENT/DATE
Content-Type: application/json
```

```json
{
  "score": 0.85,
  "confidence": 0.9,
  "ruleVersion": "manual-v0",
  "evidence": {
    "derivedFrom": ["PHOTO_SPOT", "COZY"]
  }
}
```

마지막으로 장소 상세 API를 다시 호출해 수동값과 속성·적합도가 함께 나오는지 확인한다.
