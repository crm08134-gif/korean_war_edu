import type { NextConfig } from "next";

// 백엔드(Spring Boot) 주소. 브라우저와 폰은 Next 서버(3000)에만 접속하고,
// /api 요청은 Next 서버가 PC 안에서 백엔드로 넘긴다. 그래서 CORS 설정이 필요 없다.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  // next dev가 프로젝트에 AGENTS.md(AI 도구용 안내)를 만들지 않게 한다
  agentRules: false,
  // 같은 Wi-Fi의 폰(사설 IP)에서 개발 서버를 열 수 있게 한다. 개발 서버(npm run dev)에만 적용된다
  allowedDevOrigins: ["192.168.*.*", "10.*.*.*", "172.*.*.*"],
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
      // 사건 카드 사진 (data/photos/). 백엔드가 jar 안의 static/photos/에서 내준다
      { source: "/photos/:path*", destination: `${backendUrl}/photos/:path*` },
      // 카카오·구글 로그인. 시작 주소와 돌아오는 주소를 백엔드(Spring Security)가 처리한다
      { source: "/oauth2/:path*", destination: `${backendUrl}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${backendUrl}/login/oauth2/:path*` },
    ];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
