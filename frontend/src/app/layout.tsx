import type { Metadata, Viewport } from "next";
import "@fontsource-variable/noto-sans-kr";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Korean War",
  description: "6·25 전쟁 3년 1개월 동안 전선이 어떻게 움직였는지 지도에서 넘겨 보는 앱",
  // 아이폰 "홈 화면에 추가"로 열 때 주소창 없이 열고, 아이콘 아래 이름을 정한다.
  // 아이콘은 src/app/apple-icon.png, 안드로이드 설치 정보는 src/app/manifest.ts
  appleWebApp: {
    capable: true,
    title: "Korean War",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#e9edf1",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="h-full">{children}</body>
    </html>
  );
}
