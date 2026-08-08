#!/usr/bin/env bash

set -euo pipefail

# 사용법: ./scripts/osrm-preprocess.sh [car|foot] [yeongnam]
# 영남권 범위: 부산·울산·경남을 모두 포함하고 권역 경계 도로 연결을 보존하는 확장 bbox
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
profile="${1:-car}"
region="${2:-yeongnam}"
source_dir="$repo_root/infra/osrm/data"
nationwide_pbf="$source_dir/south-korea-latest.osm.pbf"
osrm_image="${OSRM_IMAGE:-ghcr.io/project-osrm/osrm-backend:latest}"
pbf_url="https://download.geofabrik.de/asia/south-korea-latest.osm.pbf"

case "$profile" in
  car|foot) ;;
  *) echo "사용법: $0 [car|foot] [yeongnam]" >&2; exit 1 ;;
esac

case "$region" in
  yeongnam)
    region_bbox="127.2,34.3,129.7,36.1"
    ;;
  *) echo "지원하지 않는 권역입니다: $region (현재: yeongnam)" >&2; exit 1 ;;
esac

command -v docker >/dev/null || { echo "Docker가 필요합니다." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl이 필요합니다." >&2; exit 1; }
command -v osmium >/dev/null || { echo "osmium-tool이 필요합니다: brew install osmium-tool" >&2; exit 1; }

region_dir="$repo_root/infra/osrm/$region"
region_source_dir="$region_dir/source"
region_pbf="$region_source_dir/$region.osm.pbf"
data_dir="$region_dir/$profile"
output_name="$region-$profile.osrm"
output_path="/data/$output_name"
extract_threads="${OSRM_EXTRACT_THREADS:-}"
if [[ -z "$extract_threads" ]]; then
  [[ "$profile" == "foot" ]] && extract_threads=1 || extract_threads=4
fi

if compgen -G "$data_dir/*.osrm*" >/dev/null; then
  echo "기존 OSRM 전처리 결과가 있습니다: $data_dir" >&2
  echo "데이터를 덮어쓰지 않았습니다." >&2
  exit 1
fi

mkdir -p "$source_dir" "$region_source_dir" "$data_dir"
if [[ ! -f "$nationwide_pbf" ]]; then
  echo "한국 OSM 원본을 다운로드합니다."
  curl --fail --location --remote-name --output-dir "$source_dir" "$pbf_url"
fi

if [[ ! -f "$region_pbf" ]]; then
  echo "영남권 OSM 원본을 추출합니다 (bbox: $region_bbox)."
  osmium extract --strategy=complete_ways --bbox "$region_bbox" \
    -o "$region_pbf" "$nationwide_pbf"
fi

echo "OSRM 이미지: $osrm_image"
echo "권역: $region / 프로필: $profile / 추출 스레드: $extract_threads"
echo "1/3 그래프를 추출합니다."
docker run --rm -v "$data_dir:/data" -v "$region_source_dir:/source:ro" "$osrm_image" \
  osrm-extract --threads "$extract_threads" -p "/opt/$profile.lua" -o "$output_path" \
  "/source/$region.osm.pbf"

echo "2/3 MLD 파티션을 생성합니다."
docker run --rm -v "$data_dir:/data" "$osrm_image" \
  osrm-partition "$output_path"

echo "3/3 MLD 메트릭을 생성합니다."
docker run --rm -v "$data_dir:/data" "$osrm_image" \
  osrm-customize "$output_path"

if [[ "$profile" == "car" ]]; then
  echo "완료되었습니다: docker compose up -d osrm"
else
  echo "완료되었습니다: docker compose --profile walking up -d osrm-foot"
fi
