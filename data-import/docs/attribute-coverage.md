# AttributeKey 커버리지 감사

TourAPI 원천만으로 `AttributeKey` 를 실제로 얼마나 채울 수 있는지 측정한 결과다.
Purpose 판정 규칙은 여기서 확보 가능하다고 확인된 특성값 위에서만 설계한다.

- 측정일: 2026-08-08
- 대상: 수집된 원본 50,654건 (`data/raw/tourism/tour-api/`)
- 원천: `detailIntro2` (콘텐츠 타입별 표본 30건 호출)
- 결과 데이터: `mappings/attribute_sources.csv`

## 결론

`(PlaceType, AttributeKey)` 조합 69개 중 **13개(19%)만 사용 가능**하다.

| 구분 | 개수 | 의미 |
| --- | --- | --- |
| 사용 가능 | 13 | 원천 필드가 있고 커버리지 50% 이상 |
| 저커버리지 | 5 | 원천 필드는 있으나 50% 미만 |
| 원천 없음 | 51 | `detailIntro2` 에 대응 필드가 아예 없음 |

사용 가능한 13개는 전부 `PARKING_CONVENIENCE` 와 `BUSINESS_HOURS` 두 종류다.
**나머지 특성은 TourAPI만으로는 만들 수 없다.**

## PlaceType별 커버리지

| PlaceType | 사용 가능 | 확보되는 특성 |
| --- | --- | --- |
| TOURIST_SPOT | 2 / 11 | PARKING_CONVENIENCE 75%, BUSINESS_HOURS 97% |
| ACTIVITY | 1 / 10 | BUSINESS_HOURS 93% |
| ACCOMMODATION | 2 / 5 | PARKING_CONVENIENCE 96%, BUSINESS_HOURS 100% |
| SHOP | 2 / 5 | PARKING_CONVENIENCE 100%, BUSINESS_HOURS 100% |
| RESTAURANT | 2 / 9 | PARKING_CONVENIENCE 93%, BUSINESS_HOURS 100% |
| CAFE | 2 / 19 | PARKING_CONVENIENCE 93%, BUSINESS_HOURS 100% |
| BAR | 2 / 5 | PARKING_CONVENIENCE 93%, BUSINESS_HOURS 100% |
| PARKING_LOT | 0 / 5 | 없음 (TourAPI에 대응 콘텐츠 타입이 없다) |

CAFE가 가장 심하다. 19개 중 2개다. 그런데 서비스 기획이 가장 앞세우는 사례가
"공부하기 좋은 카페", "대화하기 좋은 카페"이며, 그 판정에 필요한
`NOISE_LEVEL`, `OUTLET_ACCESSIBILITY`, `WIFI_STABILITY`, `SEAT_SPACING`,
`STAY_DURATION` 은 **모두 원천이 없다.**

## 저커버리지 항목

원천 필드는 존재하지만 값이 거의 비어 있어 그대로는 쓸 수 없다.

| PlaceType.AttributeKey | 원천 필드 | 커버리지 |
| --- | --- | --- |
| TOURIST_SPOT.PRICE_LEVEL | `usefee` | 13% |
| TOURIST_SPOT.ENTRANCE_FEE_LEVEL | `usefee` | 13% |
| TOURIST_SPOT.VISIT_DURATION | `spendtime` | 2% |
| ACTIVITY.PARKING_CONVENIENCE | `parkingleports` | 7% |
| RESTAURANT.KIDS_FRIENDLY | `kidsfacility` | 4% |

`usefee` 와 `spendtime` 은 문화시설(contentType 14, 2,713건)에만 있고
관광지(contentType 12, 12,682건)에는 필드 자체가 없다. TOURIST_SPOT 전체로
가중하면 13%, 2%로 떨어진다.

## 측정 방법과 한계

- 콘텐츠 타입별로 수집된 `contentid` 중 30건을 무작위 표본(seed 고정)으로 뽑아
  `detailIntro2` 를 호출하고, 값이 빈 문자열이나 `0` 이 아닌 비율을 셌다.
- 커버리지는 콘텐츠 타입별 **수집 건수로 가중**했다. 한 PlaceType이 여러
  콘텐츠 타입에서 오는 경우(TOURIST_SPOT = 12+14+15) 단순 평균은 왜곡된다.
- **표본이 30건이라 오차가 있다.** 특히 레포츠(28)는 30건 중 14건만 응답이
  돌아왔다. 응답 실패 자체가 별도 확인이 필요한 사항이다.
- 여기서 말하는 커버리지는 **원천 필드의 존재율**이지 특성값 완성률이 아니다.
  아래 "남은 작업" 참고.

## 확보한 두 특성도 그대로는 못 쓴다

둘 다 자유 텍스트라 `AttributeKey` 의 허용 어휘로 변환하는 단계가 필요하다.

```text
parkingfood      = "가능"
opentimefood     = "- 목요일~금요일 11:00~20:00- 토요일 11:00~21:00"
restdatefood     = "매주 일요일~수요일"
```

- `PARKING_CONVENIENCE` 허용값: `GOOD / NORMAL / LIMITED / NEARBY_PARKING / DIFFICULT / UNKNOWN`
- `BUSINESS_HOURS` 허용값: `LATE / NORMAL / EARLY_CLOSE / UNKNOWN`

5단계·3단계로 나누려면 원본 값 분포를 먼저 봐야 한다. 아직 안 봤다.

## 아직 측정하지 않은 원천

`detailCommon2` 의 `overview` 가 카페 표본 24건 전부에 존재했고 평균 260자였다.
주차·야외석·분위기를 서술한 문장이 섞여 있다.

```text
"신안 천사대교 근처 압해도에 있는 ... 도로 바로 옆으로 주차장이 있어
 여유롭게 주차할 수 있다. 옆으로 작은 정원이 만들어져 있는데 벤치와
 테이블이 있어 야외에서도 음식을 즐길 수 있다."
```

다만 구조화된 필드가 아니라 홍보 문구다. **추출 가능 여부와 정확도는 측정하지
않았다.** "존재한다"까지만 확인된 상태다.

## 남은 작업

1. `parkingfood` · `opentimefood` · `restdatefood` 원본 값 분포 집계
   → 허용 어휘 변환 규칙 확정
2. `overview` 텍스트에서 특성 추출 가능성 측정 (표본 라벨링 후 정확도 비교)
3. 원천 없는 51개 조합 처리 방침 결정
   - 다른 데이터 소스를 붙일 것인가
   - `AttributeKey` 정의에서 뺄 것인가
   - `UNKNOWN` 으로 두고 운영 검수로 채울 것인가
4. 위 결과가 나온 뒤 Purpose 판정 규칙 설계

3번이 이 감사의 핵심 질문이다. 51개를 `UNKNOWN` 으로 두면 Purpose 판정은
대부분 `NEED_REVIEW` 로 떨어지고, 필터는 사실상 주차와 영업시간 두 축만 남는다.
