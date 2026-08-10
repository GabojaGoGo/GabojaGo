#!/usr/bin/env sh
# Flutter .env의 iOS 네이티브 설정만 ios/Flutter/Env.xcconfig으로 생성한다.
# 값은 출력하지 않는다. .env는 단순한 KEY=value 한 줄 형식만 사용한다.

set -eu

source_file='.env'
target_file='ios/Flutter/Env.xcconfig'
ios_keys='KAKAO_NATIVE_APP_KEY NAVER_CLIENT_ID NAVER_CLIENT_SECRET NAVER_CLIENT_NAME NAVER_URL_SCHEME GOOGLE_IOS_CLIENT_ID GOOGLE_IOS_REVERSED_CLIENT_ID GOOGLE_WEB_CLIENT_ID'

if [ ! -f "$source_file" ]; then
  echo "Missing $source_file. Copy .env.example to .env first." >&2
  exit 1
fi

get_value() {
  value=$(awk -v key="$1" '
    index($0, key "=") == 1 {
      value = substr($0, length(key) + 2)
      sub(/\r$/, "", value)
      print value
      exit
    }
  ' "$source_file")

  # flutter_dotenv는 KEY="value"를 허용한다. Xcode 설정에는 바깥 따옴표가
  # 값의 일부로 들어가면 안 되므로 한 겹만 제거한다.
  case "$value" in
    \"*\") value=$(printf '%s' "$value" | sed 's/^"//; s/"$//') ;;
    \'*\') value=$(printf '%s' "$value" | sed "s/^'//; s/'\$//") ;;
  esac
  printf '%s' "$value"
}

require_value() {
  key="$1"
  value=$(get_value "$key")
  case "$value" in
    ''|*your_*|*replace_with_*|1234567890-abcdef.apps.googleusercontent.com)
      echo "Set $key in .env before generating iOS settings." >&2
      exit 1
      ;;
  esac
}

require_value KAKAO_NATIVE_APP_KEY

# Google 설정은 모두 비어 있으면 아직 미도입한 것으로 취급한다. 하나라도 입력했다면
# iOS SDK가 불완전한 설정으로 종료되지 않도록 세 값을 함께 요구한다.
google_values_present=0
for key in GOOGLE_IOS_CLIENT_ID GOOGLE_IOS_REVERSED_CLIENT_ID GOOGLE_WEB_CLIENT_ID; do
  value=$(get_value "$key")
  case "$value" in
    ''|*your_*|*replace_with_*|1234567890-abcdef.apps.googleusercontent.com) ;;
    *) google_values_present=1 ;;
  esac
done
if [ "$google_values_present" -eq 1 ]; then
  require_value GOOGLE_IOS_CLIENT_ID
  require_value GOOGLE_IOS_REVERSED_CLIENT_ID
  require_value GOOGLE_WEB_CLIENT_ID
fi

{
  echo '// GENERATED from ../../.env. Do not edit directly.'
  echo '// Run: sh tool/generate_ios_env.sh'
  echo
  for key in $ios_keys; do
    value=$(get_value "$key")
    # 아직 도입하지 않은 provider는 빈 iOS 설정으로 둔다. 예시값을 앱에 넣지 않는다.
    case "$value" in
      *your_*|*replace_with_*|1234567890-abcdef.apps.googleusercontent.com) value='' ;;
    esac
    # Info.plist의 $(KEY) 치환은 xcconfig의 큰따옴표를 값으로 보존한다.
    # Client ID·URL Scheme에 "..."가 들어가면 네이버가 client info invalid로 거절하므로
    # 네이티브 설정 값은 따옴표 없이 기록한다.
    printf '%s = %s\n' "$key" "$value"
  done
} > "$target_file"

echo "Generated $target_file from $source_file. Rebuild iOS to apply it."
