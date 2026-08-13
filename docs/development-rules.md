# 가보자GO 개발 규칙

이 문서는 리뷰를 위한 이상적인 규칙 모음이 아니라, 로컬과 CI가 자동으로 확인할 수 있는 최소 안전망이다.

## 필수 검증

PR 전과 CI에서 루트 기준 아래 명령을 실행한다.

```bash
make verify
```

| 영역 | 명령 | 실패 시 의미 |
| --- | --- | --- |
| Backend | `./gradlew check --no-daemon` | 컴파일, 테스트, Error Prone 또는 변경 Java 파일 포맷 실패 |
| Frontend | `make frontend-analyze` | 정적 분석 오류 또는 기준선 이후 새 warning/info |
| Frontend | `flutter test` | 자동화된 동작 검증 실패 |
| Frontend 포맷 | `make format-check` | 기준 브랜치 이후 변경된 `lib/`, `test/` Dart 파일의 포맷 불일치 |

현재 analyzer 정보·warning은 `tools/frontend-analyzer-baseline.txt`에 기준선으로 관리한다. 새 진단은 파일·줄·메시지와 함께 CI를 실패시키며, 오류는 기준선과 관계없이 즉시 실패한다. 기존 진단을 의도적으로 정리하거나 승인한 경우에만 `make frontend-analyze-baseline`으로 기준선을 갱신한다.

## 프론트엔드 규칙

1. `print`는 사용하지 않는다. 개발 진단은 `debugPrint` 또는 공통 로거를 사용한다.
2. `await` 뒤 `setState`, `Navigator`, `ScaffoldMessenger` 호출 전에는 `mounted`를 확인한다.
3. API 요청은 `ApiService`에만 두며, 화면 위젯에서 URL·HTTP 헤더를 직접 만들지 않는다.
4. 장소 목록처럼 파생 상태를 변경하면 연관 상태도 같은 작업에서 갱신한다. 예: 코스 장소 교체 뒤 `routePaths`는 OSRM으로 재계산한다.
5. 새 API를 추가할 때는 응답 변환과 실패 fallback을 프론트에서 함께 구현하고, backend의 OpenAPI 예시와 요청 필드를 맞춘다.
6. `dynamic`은 JSON 디코딩·플러그인 콜백 등 외부 경계에서만 사용하고, 즉시 모델 또는 `Map<String, Object?>`로 좁힌다. 화면·상태·새 서비스 공개 API에는 추가하지 않는다.
7. 새 화면 상태는 가능하면 raw `Map<String, dynamic>` 대신 모델로 정의한다. 기존 Map 기반 코드는 기능 수정 시점에 점진적으로 치환한다.
8. 의도적으로 기다리지 않는 `Future`는 `unawaited(...)`로 표시하고, 새 Stream 구독·controller는 해제 또는 종료 경로를 함께 둔다.
9. 새 코드에는 `strict-casts`, `strict-inference`, `strict-raw-types`를 적용한다. JSON·플러그인 경계의 `dynamic`은 허용하되, 내부로 전달하기 전에 타입을 좁힌다.

### 핵심 사용자 흐름 테스트 기준

- 화면의 상태·분기·사용자 조작을 바꾸면 해당 흐름을 재현하는 위젯 테스트를 추가하거나 갱신한다.
- 실제 외부 API, 지도 SDK, OSRM 경로 계산처럼 플랫폼·네트워크에 의존하는 흐름은 위젯 테스트로 모사하지 않는다. 변경 PR에서 iOS 시뮬레이터 수동 검증 결과를 남기거나 `integration_test`로 검증 대상을 만든다.
- 장소 자동 채움처럼 지도 polyline에 영향을 주는 변경은 장소 변경 뒤 경로가 다시 계산되어 표시되는지 위 기준 중 하나로 확인한다.

## Backend 규칙

1. 새 HTTP endpoint에는 OpenAPI 설명·요청 예시·주요 오류 응답을 작성한다.
2. 새 HTTP endpoint가 호출하는 서비스에는 정상 경로와 실패/폴백 경로를 검증하는 서비스 테스트를 추가한다. endpoint만 추가하고 기존 서비스를 그대로 재사용하는 경우에도 해당 서비스 테스트가 이 요청·오류 처리를 덮는지 PR 체크리스트에 근거를 남긴다.
3. 외부 연동은 timeout과 실패 시 사용자에게 전달할 fallback을 가져야 한다.
4. Controller는 요청·응답 변환, Service는 유스케이스, Repository는 영속성 접근에 집중한다.
5. Java 운영 코드(`src/main/java`) 변경 파일은 Spotless의 `google-java-format`을 따른다. 기존 파일 일괄 포맷은 하지 않으며, Error Prone이 잡는 컴파일 단계 오류는 수정 또는 근거 있는 억제로 처리한다.

## 환경과 변경 관리

- `.env`와 원본 OSRM 데이터는 커밋하지 않는다. 필요한 키는 `.env.example`에 이름과 용도만 남긴다.
- Docker Compose의 healthcheck가 통과한 뒤에만 통합 API를 검증한다.
- `main`, `develop`에는 직접 push하지 않는다. 자세한 브랜치·커밋 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다.

## 도입 상태

- `.editorconfig`은 Java·Dart·설정 파일의 기본 들여쓰기와 줄바꿈을 맞춘다.
- Backend에는 Controller가 Repository에 직접 의존하지 않는 ArchUnit 검증과 경로 미리보기 API 계약 테스트가 있다.
- Flutter analyzer는 기준선 이후 새 warning/info를 막고, Dart 포맷 검사는 기준 브랜치 이후 변경 파일만 확인한다.
- Java는 변경 파일 기준 Spotless와 Error Prone을 사용한다. Dart strict analyzer 옵션은 baseline으로 기존 진단을 보존하고 새 진단을 차단한다.
