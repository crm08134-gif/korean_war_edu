// maplibre-gl의 워커 파일(지도 계산을 맡는 별도 스레드용 스크립트)을 public/으로 복사한다.
// 번들러가 이 파일을 자동으로 옮겨 주지 않아서, npm run dev / build 전에 복사한다 (package.json의 predev, prebuild).
// 설치된 maplibre-gl에서 복사하므로 버전이 늘 맞고, 복사본은 Git에 올리지 않는다 (.gitignore).
import { copyFileSync } from "node:fs";

const from = new URL("../node_modules/maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url);
const to = new URL("../public/maplibre-gl-worker.mjs", import.meta.url);
copyFileSync(from, to);
