#!/usr/bin/env bash
# 라벨과 마일스톤을 한 번에 만든다. 여러 번 실행해도 안전하다.
# 준비: gh CLI 설치 → gh auth login → 저장소 루트에서 실행
#   ./scripts/setup-github.sh
set -euo pipefail

label() {
  gh label create "$1" --color "$2" --description "$3" --force
}

# 종류 (Conventional Commits 타입과 맞춘다)
label "type: feat"     "1f6feb" "새 기능"
label "type: fix"      "d73a4a" "버그 수정"
label "type: refactor" "8250df" "동작은 그대로, 구조 개선"
label "type: test"     "0e8a16" "테스트 추가·수정"
label "type: docs"     "0075ca" "문서 (README, ADR, API 문서)"
label "type: chore"    "6e7781" "설정, CI/CD, 의존성"
label "type: data"     "bf8700" "전선·사건 데이터"

# 영역
label "area: backend"  "c5def5" "Spring Boot API, DB"
label "area: frontend" "f9d0c4" "Next.js, 지도 화면"
label "area: data"     "fef2c0" "GeoJSON, 적재"
label "area: infra"    "d4c5f9" "Docker, CI/CD, 배포"

# 상태
label "status: blocked" "b60205" "다른 작업이나 결정을 기다리는 중"

# 마일스톤 = 로드맵 단계 (이미 있으면 건너뛴다)
existing="$(gh api 'repos/{owner}/{repo}/milestones?state=all' --jq '.[].title')"
milestone() {
  if grep -Fxq "$1" <<<"$existing"; then
    echo "마일스톤 있음: $1"
  else
    gh api 'repos/{owner}/{repo}/milestones' -f title="$1" -f description="$2" >/dev/null
    echo "마일스톤 생성: $1"
  fi
}
milestone "1단계 · 전선과 사건" "전환점 스냅샷 데이터셋, 날짜 비율 슬라이더, 주요 사건 카드, 지인 배포"
milestone "2단계 · 사람과 삶"   "대표 인물, 그때 그 시절, 알려진 이야기 vs 실제, 관련 영상 링크"
milestone "3단계 · 확장"        "부대 이동 경로, 점령 면적 그래프, AI 안내자, 사건별 게시판·회원"
