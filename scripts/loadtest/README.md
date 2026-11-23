# 부하·용량 실험 (k6)

공개 첫 화면(`/`, `/api/snapshots`, `/api/events`, `/api/stories`, `/api/countries`)에 부하를 걸어 서버 한 대의 한계와 병목을 찾는 실험 도구다. 측정 숫자와 해석은 `RESULTS.md`.

> 운영 서버(`koreanwar-edu.tech`)에는 수십 명 규모(`korean-war-load.js`)만 보낸다. 한계까지 쏘는 실험은 아래 로컬 스택에서만 한다.

## 파일

| 파일 | 하는 일 |
| --- | --- |
| `korean-war-load.js` | VU 방식. 가상 사용자 N명이 첫 화면 5개 요청 후 3초 쉼. `GZIP=0`이면 압축 요청 안 함 |
| `korean-war-capacity.js` | 도착률 방식. 첫 화면/초를 계단(`STEPS`)으로 올리고 p95 2초·실패 5%를 넘으면 자동 중단 |
| `run-capacity.sh <이름> [Caddyfile]` | Docker 크기 검사 → 로컬 스택 띄우기 → `docker stats` 기록 → k6 → 원래 설정 복구. 조건은 `run-<이름>.meta.txt` |
| `export-metrics.sh <이름> <HH:MM> <HH:MM>` | Prometheus 지표를 `metrics-<이름>.json`으로 저장 |
| `make-charts.py <폴더>` | 결과 파일로 그림을 만든다 → `charts/`. 새 실험은 파일 안 `RUNS`에 한 줄 추가 |
| `local-stack/` | 운영과 같은 이미지·Caddy 규칙(HTTPS 포함) + Prometheus(9090)·Grafana(3001)·k6 컨테이너 |

## 로컬 스택 준비

1. `~/.wslconfig`로 Docker를 운영과 비슷한 크기로 줄인다(앱은 CPU 0·1번 2코어, 관측·k6는 2번).
   ```ini
   [wsl2]
   processors=3
   memory=3GB
   swap=2GB
   ```
   `wsl --shutdown` 뒤 Docker Desktop을 다시 켠다. 크기가 다르면 `run-capacity.sh`가 멈춘다.
2. `cp local-stack/.env.example local-stack/.env` 하고 `POSTGRES_PASSWORD`, `JWT_SECRET`을 난수로 채운다. 진짜 키는 넣지 않는다.
3. backend 안쪽 지표까지 보려면 Prometheus 지표가 들어간 backend 이미지를 쓴다: `BACKEND_IMAGE=<이미지> ./run-capacity.sh ...`

## 실행 예

```bash
./run-capacity.sh base                                  # 압축 켬
./run-capacity.sh noencode ./Caddyfile.noencode         # 압축 끔
RUNNER=docker ./run-capacity.sh noencode-docker ./Caddyfile.noencode   # k6를 Docker 네트워크 안에서
./export-metrics.sh noencode-docker 20:43 21:06
python make-charts.py .
```

- `RUNNER=docker`를 권한다. PC(Windows)의 k6는 Docker Desktop 포트 전달을 거쳐 초당 약 40MB에서 먼저 막힌다(`RESULTS.md` 참고).
- `docker compose down -v`는 쓰지 않는다. Prometheus 기록이 지워진다.
