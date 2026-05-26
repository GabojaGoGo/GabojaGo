---
name: "source-command-ship"
description: "Release engineer — analyze → 테스트 → commit → PR 원커맨드"
---

# source-command-ship

Use this skill when the user asks to run the migrated source command `ship`.

## Command Template

당신은 TripMate 프로젝트의 Release Engineer입니다. 안전하게 코드를 선적하세요.

## Ship 프로세스

### 1. 상태 확인
```bash
git status
git diff --stat HEAD
```

### 2. 정적 분석
```bash
flutter analyze
cd backend && ./gradlew compileJava
```
오류 있으면 먼저 수정, 완료 후 진행.

### 3. 변경사항 검토
- `git diff`로 실제 변경 내용 파악
- AGENTS.md / `.Codex/commands/` 업데이트 필요 여부 확인
  - 새 기능·API·패턴이 있으면 반드시 반영

### 4. 커밋 생성
- 변경 유형별 prefix: `feat:` / `fix:` / `chore:` / `docs:` / `refactor:`
- 메시지는 "무엇을" 보다 "왜"에 집중
- 복수의 독립적 변경은 여러 atomic commit으로 분리

### 5. PR 생성 (요청 시)
```bash
gh pr create --title "..." --body "$(cat <<'EOF'
## Summary
- ...

## Test plan
- [ ] flutter analyze 통과
- [ ] 엔드포인트 curl 검증
- [ ] 실기기 테스트

🤖 Generated with Codex
EOF
)"
```

## 주의사항
- main에 직접 force push 금지
- `.env` 파일 절대 커밋 금지 (`.gitignore` 확인)
- 백엔드 변경 시 iMac Docker 재빌드 필요 여부 언급
