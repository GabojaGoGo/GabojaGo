"""P1 base staging 정제에서 공용으로 쓰는 정규화·매칭 유틸리티.

analyze_p1_staging_v2.py(2차 검증)와 stage_p1_sources.py(staging 생성)가
각각 독립적으로 이름/주소 정규화 로직을 구현하면서 76건 규모의 결과 불일치가
발생한 적이 있다(matching key 로직 자체는 동일했으나, HIGH/MEDIUM tier를
결합해 세는지 여부가 스크립트마다 달랐다). 두 스크립트가 반드시 이 모듈의
함수를 사용하게 해서 재현성을 보장한다.
"""

from __future__ import annotations

import math
import re

from pyproj import Transformer

BUUL_LAT = (34.5, 36.0)
BUUL_LON = (127.5, 129.8)

EPSG5174_TO_WGS84 = Transformer.from_crs("EPSG:5174", "EPSG:4326", always_xy=True)

STATUS_MAP = {
    # ACTIVE — base 5종에서 이미 쓰던 값
    "영업": "ACTIVE",
    "영업/정상": "ACTIVE",
    "영업중": "ACTIVE",
    # ACTIVE — 확장 LOCALDATA 업종 전수 측정으로 새로 확인(2026-08-26).
    # "정상"(농어촌민박업 전량), "정상영업"(대규모점포)은 다른 업종의 "영업"과
    # 같은 자리에 오는 동의어다(같은 파일 안에 "영업"이 아예 없다).
    "정상": "ACTIVE",
    "정상영업": "ACTIVE",
    # "지정"/"승인"은 전통사찰 전용 어휘 — 372건 모집단(8-1 실측)이
    # "영업/정상"(154)+"지정"(216)+"승인"(2)의 합이라 셋 다 ACTIVE로 묶지
    # 않으면 전통사찰 모집단이 41% 과소집계된다.
    "지정": "ACTIVE",
    "승인": "ACTIVE",
    # CLOSED — base 5종에서 이미 쓰던 값
    "폐업": "CLOSED",
    "등록취소": "CLOSED",
    "직권말소": "CLOSED",
    # CLOSED — 확장 LOCALDATA 업종에서 새로 확인. "폐업처리"/"영업장폐쇄"는
    # "폐업"과 동의어, "직권취소"/"신고취소"/"허가취소"/"지정취소"는 처분
    # 주체만 다른 등록취소류다.
    "폐업처리": "CLOSED",
    "영업장폐쇄": "CLOSED",
    "직권취소": "CLOSED",
    "신고취소": "CLOSED",
    "허가취소": "CLOSED",
    "지정취소": "CLOSED",
    # SUSPENDED — base 5종에서 이미 쓰던 값
    "휴업": "SUSPENDED",
    # SUSPENDED — "휴업처리"는 "휴업"과 동의어. "영업정지"는 행정처분에 의한
    # 일시 정지로 폐업이 아니다(1건, 대규모점포 계열이 아니라 문화_일반테마파크업).
    "휴업처리": "SUSPENDED",
    "영업정지": "SUSPENDED",
    # 의도적으로 매핑하지 않고 UNKNOWN으로 남기는 값(정상/폐업 어느 쪽도 확정 못함):
    #   "전출"/"타시군구이관" — 사업 종료가 아니라 관할 이전. 종료로 단정하면
    #     실제 운영 중인 시설을 CLOSED 처리하는 오류가 된다. original_status
    #     필드에 원문이 그대로 남아 이후 재판단이 가능하다.
    #   "영업개시전" — 아직 개업 전. ACTIVE도 CLOSED도 아니다.
    #   "조건이행완료" — 조건부 허가의 조건 이행 완료 표시로 추정되나 그
    #     자체가 영업 여부를 확정하지 않는다(9건, 소규모).
}

LOT_ADDR_RE = re.compile(r"^(\S+\s+\S+\s+\S+\s+\d+(-\d+)?)")


def normalize_str(value: str | None) -> str | None:
    if value is None:
        return None
    value = re.sub(r"\s+", " ", value.strip())
    return value or None


def norm_nospace(value: str | None) -> str:
    """이름 매칭용 — 공백 제거 + 대소문자 무시(영문 상호 대응)."""
    if not value:
        return ""
    return re.sub(r"\s+", "", value.strip()).lower()


def lot_addr_key(value: str | None) -> str | None:
    """지번주소를 '시도 시군구 동 번지'까지만 남긴다(건물명/층 등 절단).

    패턴에 맞지 않는(토큰이 4개 미만이거나 번지 숫자가 없는) 주소는 정규화된
    원문 전체를 키로 폴백한다. 조기에 None을 반환해 색인에서 제외하면 실제
    매칭 가능한 레코드를 누락시킨다(2026-08 3차 정제에서 실측 확인 — 폴백을
    None으로 처리했더니 고신뢰 매칭이 73,477건에서 55,126건으로 줄었다).
    """
    normalized = normalize_str(value)
    if not normalized:
        return None
    m = LOT_ADDR_RE.match(normalized)
    return m.group(1) if m else normalized


ADDRESS_DIGIT_RE = re.compile(r"\d")


AREA_NAME_KEYWORDS = (
    "공원", "해수욕장", "해변", "해안", "유원지", "생태공원", "휴양림", "수목원",
    "등산로", "둘레길", "자연휴양림", "치유의숲", "계곡", "저수지", "호수", "국립공원",
    "도립공원", "군립공원", "습지", "생태관광지",
)

MOUNTAIN_SUFFIX_RE = re.compile(r"(^|\s)\S+산(\s|$)")


def is_area_name(name: str | None) -> bool:
    """면적형(산·공원·해변 등) 장소 이름 여부 — entity resolution·내부 dedupe에서
    공통으로 자동 처리 대상을 제외할 때 쓴다. Place 단위 설계는 P2 몫이다.

    "산"은 다른 키워드처럼 단순 substring으로 보면 "부산"·"울산"처럼 지명
    접두어에 우연히 들어간 경우까지 면적형으로 오판한다("부산현대미술관" 등
    실제 점형 시설 3건이 원천 내부 dedupe에서 잘못 제외된 사례로 확인됨).
    "산"은 이름 전체가 그 글자로 끝나거나 공백으로 구분된 토큰이 "산"으로
    끝날 때만(예: "금정산", "지리산 둘레길") 면적형으로 판정한다.
    """
    if not name:
        return False
    if any(kw in name for kw in AREA_NAME_KEYWORDS):
        return True
    return bool(name.endswith("산") or MOUNTAIN_SUFFIX_RE.search(name))


def classify_address_precision(*addresses: str | None) -> str:
    """entity resolution용 주소 정밀도 분류 — PRECISE/COARSE/MISSING.

    PRECISE: 도로명+건물번호 또는 지번(번지)처럼 숫자로 특정 지점을 가리키는 주소.
    COARSE: 시/군/구+읍/면/동/리까지만 있고 번지·건물번호가 없는 주소
      (예: "부산광역시 기장군 장안읍 임랑리" — 실측 결과 이 수준의 주소는 같은
      이름의 서로 다른 시설이 우연히 일치할 위험이 있어 HIGH 승격 신호로 쓰지 않는다).
    판정은 주소 문자열에 숫자가 있는지만 본다 — 새로 추론하거나 지오코딩하지 않는다.
    """
    candidates = [normalize_str(a) for a in addresses if normalize_str(a)]
    if not candidates:
        return "MISSING"
    if any(ADDRESS_DIGIT_RE.search(a) for a in candidates):
        return "PRECISE"
    return "COARSE"


def normalize_status(raw_value: str | None) -> tuple[str, str]:
    original = normalize_str(raw_value) or ""
    if not original:
        return "UNKNOWN", original
    return STATUS_MAP.get(original, "UNKNOWN"), original


def check_coordinate(lat: float | None, lon: float | None) -> str:
    if lat is None or lon is None:
        return "MISSING"
    if lat == 0 or lon == 0:
        return "ZERO"
    if not (BUUL_LAT[0] <= lat <= BUUL_LAT[1] and BUUL_LON[0] <= lon <= BUUL_LON[1]):
        return "OUT_OF_REGION"
    return "VALID"


def to_float(value: str | None) -> float | None:
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def epsg5174_to_wgs84(x: float | None, y: float | None) -> tuple[float | None, float | None]:
    if x is None or y is None or x == 0 or y == 0:
        return None, None
    lon, lat = EPSG5174_TO_WGS84.transform(x, y)
    return lat, lon


def haversine_m(lat1, lon1, lat2, lon2) -> float | None:
    if None in (lat1, lon1, lat2, lon2):
        return None
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))
