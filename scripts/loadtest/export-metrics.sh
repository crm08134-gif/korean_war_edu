#!/usr/bin/env bash
# Prometheus에 쌓인 지표를 시간 범위로 뽑아 파일로 남긴다 (Claude가 읽고 분석하는 용도)
#   사용법: ./export-metrics.sh <이름> <시작 HH:MM> <끝 HH:MM>     예: ./export-metrics.sh base-obs 19:07 19:21
# 결과: metrics-<이름>.json
set -euo pipefail
NAME="${1:?이름}"; FROM="${2:?시작 HH:MM}"; TO="${3:?끝 HH:MM}"
cd "$(dirname "$0")"
START=$(date -d "today $FROM" +%s); END=$(date -d "today $TO" +%s)
PROM=http://localhost:9090/api/v1/query_range
declare -A Q=(
  [k6_rps]='sum(rate(k6_http_reqs_total[30s]))'
  [caddy_rps]='sum(rate(caddy_http_requests_total[30s]))'
  [backend_rps]='sum(rate(http_server_requests_seconds_count[30s]))'
  [k6_p95]='max(k6_http_req_duration_p95)'
  [caddy_p95]='histogram_quantile(0.95, sum by (le) (rate(caddy_http_request_duration_seconds_bucket[30s])))'
  [backend_p95]='histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[30s])))'
  [caddy_cpu]='rate(process_cpu_seconds_total{job="caddy"}[30s])'
  [backend_cpu]='process_cpu_usage{job="backend"} * system_cpu_count{job="backend"}'
  [heap_used]='sum(jvm_memory_used_bytes{area="heap"})'
  [heap_max]='sum(jvm_memory_max_bytes{area="heap"})'
  [gc_pause_ratio]='sum(rate(jvm_gc_pause_seconds_sum[30s]))'
  [gc_per_sec]='sum(rate(jvm_gc_pause_seconds_count[30s]))'
  [tomcat_busy]='sum(tomcat_threads_busy_threads)'
  [tomcat_max]='sum(tomcat_threads_config_max_threads)'
  [hikari_active]='sum(hikaricp_connections_active)'
  [hikari_pending]='sum(hikaricp_connections_pending)'
  [caddy_inflight]='sum(caddy_http_requests_in_flight)'
  [k6_vus]='max(k6_vus)'
  [caddy_mem]='process_resident_memory_bytes{job="caddy"}'
  [backend_rss]='process_resident_memory_bytes{job="backend"}'
)
OUT="metrics-$NAME.json"
echo "{" > "$OUT"; first=1
for k in "${!Q[@]}"; do
  r=$(curl -s --get "$PROM" --data-urlencode "query=${Q[$k]}" --data-urlencode "start=$START" --data-urlencode "end=$END" --data-urlencode "step=5")
  [ $first = 1 ] && first=0 || echo "," >> "$OUT"
  printf '"%s": %s' "$k" "$r" >> "$OUT"
done
echo "}" >> "$OUT"
echo "저장: $OUT ($(wc -c < "$OUT") bytes)"
