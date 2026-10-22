import type { Metadata, Viewport } from "next";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "6·25 전쟁 지도",
  description: "6·25 전쟁 3년 1개월 동안 전선이 어떻게 움직였는지 지도에서 넘겨 보는 앱",
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
