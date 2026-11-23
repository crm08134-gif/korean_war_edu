"""부하 실험 결과를 그림(PNG)으로 남긴다. 저장된 파일만 읽으므로 나중에 다시 돌려도 같은 그림이 나온다.
사용법: python make-charts.py <loadtest 폴더>   →  <폴더>/charts/*.png
읽는 것: report-capacity-*.html(k6 대시보드, 10초 구간), local-stack/stats-capacity-*.log(docker stats 5초),
        metrics-*.json(Prometheus, 있으면)
"""
import base64, datetime as dt, gzip, json, os, re, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

KST = dt.timezone(dt.timedelta(hours=9))
for name in ("Noto Sans CJK KR", "Malgun Gothic", "NanumGothic", "AppleGothic"):
    if any(name in f.name for f in font_manager.fontManager.ttflist):
        plt.rcParams["font.family"] = name; break
plt.rcParams["axes.unicode_minus"] = False

# 실험 목록: (파일 이름에 쓸 이름, 그림 제목, k6 보고서, docker stats 기록, Prometheus 지표)
RUNS = [
    ("1118-base", "11-18 압축 켬 · PC에서 k6", "report-capacity.html", "local-stack/stats-capacity.log", None),
    ("1119-noencode", "11-19 압축 끔 · PC에서 k6", "report-capacity-noencode.html", "local-stack/stats-capacity-noencode.log", None),
    ("1123-base-obs", "11-23 압축 켬 · PC에서 k6 · 관측", "report-capacity-base-obs.html", "local-stack/stats-capacity-base-obs-1123-1908.log", "metrics-base-obs.json"),
    ("1123-noencode-obs", "11-23 압축 끔 · PC에서 k6 · 관측", "report-capacity-noencode-obs.html", "local-stack/stats-capacity-noencode-obs-1123-2009.log", "metrics-noencode-obs.json"),
    ("1123-noencode-docker", "11-23 압축 끔 · Docker 안에서 k6", "report-capacity-noencode-docker.html", "local-stack/stats-capacity-noencode-docker-1123-2043.log", "metrics-noencode-docker.json"),
]
COLORS = {"caddy": "#2f6fd6", "backend": "#e07b25", "frontend": "#6aa84f", "db": "#8e7cc3", "k6": "#999999"}


def k6_series(path):
    """k6 웹 대시보드 HTML 안의 10초 구간 기록을 꺼낸다 → (시작 시각, [(경과초, 요청/초, p95 ms, 중앙값 ms, 실패율)])"""
    html = open(path, encoding="utf-8").read()
    data = re.search(r'<script[^>]*id="data"[^>]*>(.*?)</script>', html, re.S).group(1).strip()
    lines = [json.loads(l) for l in gzip.decompress(base64.b64decode(data)).decode().splitlines() if l.strip()]
    known, start, out = [], None, []
    for l in lines:
        if l["event"] == "metric":
            known += list(l["data"].keys())
        elif l["event"] == "start":
            start = l["data"][0][0] / 1000
        elif l["event"] == "snapshot":
            m = dict(zip(sorted(known), l["data"]))
            def g(k, i):
                try:
                    return float(m[k][i])
                except (KeyError, IndexError, TypeError):
                    return float("nan")
            # trend 순서: avg, max, med, min, p(90), p(95), p(99)
            out.append((m["time"][0] / 1000 - start, g("http_reqs", 1), g("http_req_duration", 5),
                        g("http_req_duration", 2), g("http_req_failed", 0)))
    return start, out


def stats_series(path, start):
    """docker stats 기록 → {컨테이너: [(경과초, CPU 코어, 메모리 MiB)]}"""
    res = {}
    day = dt.datetime.fromtimestamp(start, KST).date()
    for line in open(path, encoding="utf-8", errors="ignore"):
        line = line.replace("\x00", "")
        p = line.split()
        if len(p) < 4 or not re.match(r"\d\d:\d\d:\d\d$", p[0]) or "%" not in p[2]:
            continue
        name = re.sub(r"^korean-war-loadtest-|-1$", "", p[1])
        if name.startswith("k6-run"):
            name = "k6"
        if name not in COLORS:
            continue
        t = dt.datetime.combine(day, dt.time.fromisoformat(p[0]), KST).timestamp() - start
        mem = p[3]
        mib = float(re.sub(r"[A-Za-z]", "", mem)) * (1024 if mem.endswith("GiB") else 1)
        res.setdefault(name, []).append((t, float(p[2].rstrip("%")) / 100, mib))
    return res


def prom_series(path, start):
    m = json.load(open(path, encoding="utf-8"))
    out = {}
    for k, v in m.items():
        r = v.get("data", {}).get("result", [])
        if r:
            out[k] = [(float(t) - start, float(x)) for t, x in r[0]["values"] if x != "NaN"]
    return out


def mins(xs):
    return [x / 60 for x in xs]


def run_chart(base, key, title, rep, st, prom, outdir):
    start, k6 = k6_series(os.path.join(base, rep))
    rows = 3 if prom and os.path.exists(os.path.join(base, prom)) else 2
    fig, axes = plt.subplots(rows, 1, figsize=(11, 3.4 * rows), sharex=True)
    when = dt.datetime.fromtimestamp(start, KST).strftime("%Y-%m-%d %H:%M")
    fig.suptitle(f"{title}  (시작 {when} KST)", fontsize=13, x=0.01, ha="left")

    ax = axes[0]
    t = mins([r[0] for r in k6])
    ax.plot(t, [r[1] for r in k6], color="#2f6fd6", label="받은 요청/초 (k6 기준)")
    ax.set_ylabel("요청/초")
    ax2 = ax.twinx()
    ax2.plot(t, [r[2] for r in k6], color="#d62f2f", lw=1.2, label="p95 (ms, 로그 눈금)")
    ax2.axhline(2000, color="#d62f2f", ls="--", lw=0.8)
    ax2.set_yscale("log"); ax2.set_ylabel("p95 ms (로그)")
    ax.set_title("처리량과 응답 시간 (10초 구간) · 빨간 점선 = 자동 중단 기준 2초", fontsize=10, loc="left")
    h1, l1 = ax.get_legend_handles_labels(); h2, l2 = ax2.get_legend_handles_labels()
    ax.legend(h1 + h2, l1 + l2, loc="upper left", fontsize=8)

    ax = axes[1]
    for name, pts in stats_series(os.path.join(base, st), start).items():
        ax.plot(mins([p[0] for p in pts]), [p[1] for p in pts], color=COLORS[name], lw=1.1, label=name)
    ax.axhline(2, color="#555", ls=":", lw=0.8)
    ax.set_ylabel("CPU (코어)"); ax.set_ylim(bottom=0)
    ax.set_title("컨테이너별 CPU (docker stats 5초 표본) · 점선 = 앱이 쓸 수 있는 2코어", fontsize=10, loc="left")
    ax.legend(loc="upper left", fontsize=8, ncol=5)

    if rows == 3:
        ax = axes[2]
        p = prom_series(os.path.join(base, prom), start)
        for k, label, color in (("caddy_p95", "Caddy가 잰 p95", "#2f6fd6"), ("backend_p95", "backend 안 p95", "#e07b25")):
            if k in p:
                ax.plot(mins([x[0] for x in p[k]]), [x[1] * 1000 for x in p[k]], color=color, label=label)
        ax.set_yscale("log"); ax.set_ylabel("p95 ms (로그)")
        ax3 = ax.twinx()
        if "heap_used" in p:
            ax3.plot(mins([x[0] for x in p["heap_used"]]), [x[1] / 2**20 for x in p["heap_used"]], color="#999", lw=0.9, label="backend 힙 MiB")
        ax3.set_ylabel("힙 MiB (최대 512)"); ax3.set_ylim(0, 520)
        ax.set_title("서버 안쪽 (Prometheus): Caddy가 잰 시간 vs backend 처리 시간, backend 힙", fontsize=10, loc="left")
        h1, l1 = ax.get_legend_handles_labels(); h2, l2 = ax3.get_legend_handles_labels()
        ax.legend(h1 + h2, l1 + l2, loc="upper left", fontsize=8)

    axes[-1].set_xlabel("경과 시간 (분)")
    fig.tight_layout()
    path = os.path.join(outdir, f"run-{key}.png")
    fig.savefig(path, dpi=110); plt.close(fig)
    return k6


def compare_chart(series, outdir):
    fig, ax = plt.subplots(figsize=(10, 5.5))
    for title, k6, color in series:
        pts = [(r[1], r[2]) for r in k6 if r[1] == r[1] and r[2] == r[2] and r[1] > 0]
        ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=12, alpha=0.75, color=color, label=title)
    ax.set_yscale("log"); ax.axhline(2000, color="#d62f2f", ls="--", lw=0.8)
    ax.set_xlabel("실제로 처리한 요청/초 (10초 구간)"); ax.set_ylabel("p95 ms (로그)")
    ax.set_title("실험 비교: 요청이 늘 때 응답 시간이 언제 치솟나 (오른쪽으로 갈수록 더 많이 버팀)", loc="left", fontsize=11)
    ax.legend(fontsize=8, loc="upper left")
    fig.tight_layout()
    fig.savefig(os.path.join(outdir, "compare-capacity.png"), dpi=110); plt.close(fig)


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "."
    outdir = os.path.join(base, "charts"); os.makedirs(outdir, exist_ok=True)
    palette = ["#9db7e6", "#f2b98a", "#2f6fd6", "#e07b25", "#6aa84f"]
    series = []
    for (key, title, rep, st, prom), color in zip(RUNS, palette):
        if not os.path.exists(os.path.join(base, rep)):
            print("건너뜀(파일 없음):", rep); continue
        k6 = run_chart(base, key, title, rep, st, prom, outdir)
        series.append((title, k6, color))
        print("그림:", f"charts/run-{key}.png")
    compare_chart(series, outdir); print("그림: charts/compare-capacity.png")


if __name__ == "__main__":
    main()


# ---------- 운영 서버(시드니) 50명 테스트: 병목이 옮겨 간 모습 ----------
# 서버의 docker stats 기록(UTC 시각)을 내려받아 prod/ 에 둔다. k6 시간 기록은 없어 CPU만 그린다
PROD = [
    ("prod/stats-50vu-1116-2303-before-cache.log", "① 11-16 23:03 캐시 전 · p95 1.96초", "db"),
    ("prod/stats-50vu-1117-0248-cache-gzip.csv", "② 11-17 02:48 메모리 캐시(#92) + 압축 요청 · p95 1.37초", "frontend"),
    ("prod/stats-50vu-1117-1352-caddy-direct.log", "③ 11-17 13:52 Caddy가 backend로 바로 전달(#93) · p95 0.63초", "caddy"),
]


def prod_stats(path):
    rows = {}
    t0 = None
    for line in open(path, encoding="utf-8", errors="ignore"):
        p = [x for x in re.split(r"[,\s]+", line.strip()) if x]
        if len(p) < 4 or not re.match(r"\d\d:\d\d:\d\d$", p[0]) or "%" not in p[2]:
            continue
        h, m, s = map(int, p[0].split(":"))
        sec = h * 3600 + m * 60 + s
        t0 = sec if t0 is None else t0
        name = re.sub(r"^korean-war-|-1$", "", p[1])
        if name in COLORS:
            rows.setdefault(name, []).append(((sec - t0) / 60, float(p[2].rstrip("%")) / 100))
    return rows


def prod_chart(base, outdir):
    have = [r for r in PROD if os.path.exists(os.path.join(base, r[0]))]
    if not have:
        return
    fig, axes = plt.subplots(len(have), 1, figsize=(11, 3.0 * len(have)), sharey=True)
    fig.suptitle("운영 서버(Lightsail 시드니, vCPU 2) · 가상 사용자 50명 5분 · 컨테이너별 CPU: 병목이 DB → Next → Caddy로 옮겨 갔다",
                 fontsize=12, x=0.01, ha="left")
    for ax, (path, title, hot) in zip(axes, have):
        for name, pts in prod_stats(os.path.join(base, path)).items():
            ax.plot([p[0] for p in pts], [p[1] for p in pts], color=COLORS[name],
                    lw=2.2 if name == hot else 1.0, alpha=1 if name == hot else 0.6, label=name + (" ← 가장 바쁨" if name == hot else ""))
        ax.axhline(2, color="#555", ls=":", lw=0.8)
        ax.set_ylim(0, 2.1); ax.set_ylabel("CPU (코어)")
        ax.set_title(title, fontsize=10, loc="left")
        ax.legend(loc="upper right", fontsize=8, ncol=4)
    axes[-1].set_xlabel("기록 시작부터 경과 시간 (분, 5~7초 표본)")
    fig.tight_layout()
    fig.savefig(os.path.join(outdir, "prod-bottleneck-shift.png"), dpi=110); plt.close(fig)
    print("그림: charts/prod-bottleneck-shift.png")


if __name__ == "__main__":
    _base = sys.argv[1] if len(sys.argv) > 1 else "."
    prod_chart(_base, os.path.join(_base, "charts"))
