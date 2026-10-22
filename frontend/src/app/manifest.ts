import type { MetadataRoute } from "next";

// 홈 화면에 설치(PWA)할 때 쓰는 앱 정보. /manifest.webmanifest로 나간다.
// 아이콘은 scripts/make-app-icons.mjs로 만든다(npm run icons). 색은 src/lib/map-style.ts의 바다색과 같다
export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "Korean War",
    short_name: "Korean War",
    description: "6·25 전쟁 3년 1개월 동안 전선이 어떻게 움직였는지 지도에서 넘겨 보는 앱",
    lang: "ko",
    start_url: "/",
    scope: "/",
    // 주소창 없이 앱처럼 연다. 화면이 세로 배치라 세로로 고정한다
    display: "standalone",
    orientation: "portrait",
    background_color: "#e9edf1",
    theme_color: "#e9edf1",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      // 안드로이드가 원·둥근 사각형으로 잘라 쓰는 아이콘. 철모가 가운데 80% 원 안에 있다
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
