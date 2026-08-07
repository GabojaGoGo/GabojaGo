.DEFAULT_GOAL := verify

.PHONY: verify backend-verify frontend-verify frontend-analyze frontend-analyze-baseline frontend-test format-check

# 로컬과 CI가 같은 검증 명령을 사용한다.
verify: backend-verify frontend-verify

backend-verify:
	cd gabojago-backend && ./gradlew check --no-daemon

frontend-verify: frontend-analyze frontend-test

frontend-analyze:
	./scripts/check-dart-analyzer-baseline.sh

# 기준선 갱신은 analyzer 경고를 의도적으로 정리한 PR에서만 실행한다.
frontend-analyze-baseline:
	./scripts/check-dart-analyzer-baseline.sh --write-baseline

frontend-test:
	./scripts/run-flutter-test.sh

# 변경 파일의 자동 포맷은 커밋 전에 별도로 수행한다.
format-check:
	./scripts/check-dart-format.sh
