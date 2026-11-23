#!/usr/bin/env bash
# 용량 산정 한 번을 처음부터 끝까지 돌린다.
#   사용법: ./run-capacity.sh <이름> [Caddyfile]
#   예:     ./run-capacity.sh noencode ./Caddyfile.noencode
#           ./run-capacity.sh baseline
# 결과: result-capacity-<이름>.json, report-capacity-<이름>.html, local-stack/stats-capacity-<이름>.log
set -euo pipefail
NAME="${1:?이름을 주세요. 예: ./run-capacity.sh noencode ./Caddyfile.noencode}"
CADDY="${2:-./Caddyfile}"
cd "$(dirname "$0")"

echo "== 1. Docker 크기 확인 (vCPU 3·메모리 3GB: 앱은 CPU 0·1번 2개, 관측 도구는 2번)"
CPUS=$(docker info --format '{{.NCPU}}')
MEM=$(docker info --format '{{.MemTotal}}')
echo "   CPUs=$CPUS, Memory=$((MEM / 1024 / 1024))MiB"
if [ "$CPUS" != "3" ] || [ "$MEM" -gt 3500000000 ]; then
  echo "!! 크기가 운영과 다릅니다. ~/.wslconfig(processors=3, memory=3GB)를 만들고 wsl --shutdown 뒤 Docker Desktop을 다시 켜세요. 멈춥니다."
  exit 1
fi

echo "== 2. 잡음이 되는 개발용 DB 끄기"
docker stop korean-war-map-db-1 >/dev/null 2>&1 || true

echo "== 3. 로컬 서버 띄우기 (Caddy 설정: $CADDY)"
(cd local-stack && CADDYFILE="$CADDY" docker compose up -d --force-recreate caddy backend && CADDYFILE="$CADDY" docker compose up -d)
echo "   backend 이미지: ${BACKEND_IMAGE:-ghcr.io 운영 이미지}"
echo -n "   백엔드 준비 기다리는 중"
for i in $(seq 1 60); do
  if curl -skf https://localhost:8443/api/countries -o /dev/null; then echo " 완료"; break; fi
  echo -n "."; sleep 3
  if [ "$i" = 60 ]; then echo; echo "!! 3분 안에 준비되지 않았습니다. docker compose logs backend 를 보세요."; exit 1; fi
done
curl -sk -H 'Accept-Encoding: gzip' -D - -o /dev/null https://localhost:8443/api/snapshots | grep -iE "content-encoding" || echo "   (압축 없음)"

META="run-$NAME.meta.txt"
{
  echo "이름: $NAME"
  echo "시작: $(date "+%Y-%m-%d %H:%M:%S %Z")"
  echo "Docker: CPUs=$CPUS, Memory=$((MEM / 1024 / 1024))MiB"
  echo "Caddy 설정: $CADDY"
  echo "backend 이미지: ${BACKEND_IMAGE:-ghcr.io 운영 이미지}"
  echo "계단(첫 화면/초): ${STEPS:-스크립트 기본값}"
  echo "부하 발생기: ${RUNNER:-pc} (pc = Windows k6, docker = Docker 네트워크 안 k6 컨테이너, CPU 2번)"
  echo "저장소 커밋: $(git log --oneline -1 2>/dev/null || echo 알 수 없음)"
} > "$META"
echo "   조건 기록: $META"

echo "== 4. 서버 기록 시작 (5초마다, 30분 뒤 저절로 멈춤)"
LOG="local-stack/stats-capacity-$NAME-$(date +%m%d-%H%M).log"
nohup timeout 1800 sh -c 'while true; do docker stats --no-stream --format "{{.Name}} {{.CPUPerc}} {{.MemUsage}}" | sed "s/^/$(date +%T) /"; sleep 5; done' > "$LOG" 2>&1 &
STATS_PID=$!

echo "== 5. 계단식 부하 (최대 22분, 기준을 넘으면 저절로 멈춤). 그래프: Grafana http://localhost:3001 , k6 http://localhost:5665"
set +e
if [ "${RUNNER:-pc}" = "docker" ]; then
  # k6를 Docker 네트워크 안(CPU 2번)에서 돌린다. PC → Docker 포트 전달을 거치지 않는다
  (cd local-stack && MSYS_NO_PATHCONV=1 CADDYFILE="$CADDY" docker compose run --rm \
    -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT="report-capacity-$NAME.html" -e STEPS="${STEPS:-}" \
    k6 run -o experimental-prometheus-rw --tag testid="$NAME" --summary-export="result-capacity-$NAME.json" korean-war-capacity.js)
else
  K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT="report-capacity-$NAME.html" \
    K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write K6_PROMETHEUS_RW_TREND_STATS="p(95),avg" \
    k6 run -o experimental-prometheus-rw --tag testid="$NAME" --summary-export="result-capacity-$NAME.json" korean-war-capacity.js
fi
set -e

echo "== 6. 정리"
kill "$STATS_PID" >/dev/null 2>&1 || true
(cd local-stack && docker compose up -d --force-recreate caddy >/dev/null 2>&1) || true
echo "끝: $(date "+%H:%M:%S")" >> "$META"
echo "끝. Caddy는 원래 설정(압축 켬)으로 되돌렸습니다. 결과 파일: result-capacity-$NAME.json, report-capacity-$NAME.html, $LOG"
