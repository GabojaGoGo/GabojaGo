# 가보자GO 개발자 가이드

처음 참여하는 개발자가 로컬 실행, 변경 검증, PR 준비까지 같은 흐름으로 작업하기 위한 안내다.

## 시작 전 준비

- Docker Desktop: MySQL·Redis·OSRM 컨테이너 실행에 사용한다.
- JDK 21: backend Gradle 빌드에 사용한다.
- Flutter 3.44.8: frontend 검증과 앱 실행에 사용한다.
- 민감한 값은 각 프로젝트의 `.env`에만 넣고 커밋하지 않는다.

프로젝트 루트에서 기본 검증을 실행한다.

```bash
make verify
```

백엔드만 확인하려면 `make backend-verify`, 프론트만 확인하려면 `make frontend-verify`를 쓴다. 변경한 Dart 파일의 포맷은 PR 전에 아래 명령으로 확인한다.

```bash
make format-check
```

## 일상 작업 흐름

1. `develop`에서 작업 브랜치를 만든다.
2. 기능과 함께 필요한 테스트·API 문서를 추가한다.
3. `make verify`와 `make format-check`를 통과시킨다.
4. PR에는 사용자에게 보이는 변화, API 변화, 검증 결과를 짧게 적는다.

`main`, `develop`에는 직접 push하지 않는다. 브랜치와 커밋 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

## API를 추가하거나 바꿀 때

- Controller에 OpenAPI 설명, 요청 예시, 주요 오류 응답을 작성한다.
- Service의 정상 흐름과 외부 연동 실패/폴백을 테스트한다.
- 프론트는 `ApiService`를 통해 요청하고, 화면에서 URL이나 HTTP 헤더를 직접 만들지 않는다.
- 요청·응답 필드가 바뀌면 backend 계약 테스트와 프론트 변환 모델을 함께 바꾼다.

## Flutter 데이터 규칙

`dynamic`은 JSON 디코딩, 플랫폼 플러그인 콜백처럼 타입을 알 수 없는 **경계**에서만 허용한다. 경계를 넘은 뒤에는 즉시 모델 또는 `Map<String, Object?>`로 좁힌다.

- 화면 위젯, 상태, 비즈니스 로직, 새 서비스 공개 API에는 `dynamic`을 추가하지 않는다.
- API 응답은 `ApiService` 또는 전용 모델의 `fromJson`에서 한 번만 변환한다.
- 기존 `Map<String, dynamic>` 코드는 기능을 수정하는 시점에 작은 단위로 모델화한다. 전체를 한 번에 바꾸지 않는다.

`RoutePreviewPath`는 이 규칙의 예시다. OSRM 응답은 모델로 변환된 뒤 지도 위젯의 기존 호환 형식으로만 바뀐다.

## 경고 기준선

기존 Flutter analyzer warning/info는 `tools/frontend-analyzer-baseline.txt`에 기록돼 있다. CI는 새 진단만 실패로 처리한다. 기존 진단을 의도적으로 없앴다면 아래로 기준선을 갱신하고, PR에 사유를 남긴다.

```bash
make frontend-analyze-baseline
```

더 자세한 자동 규칙과 예외는 [development-rules.md](development-rules.md)를 참고한다.
