# 프론트 이미지. frontend/ 폴더만 있으면 되므로 그 폴더를 기준으로 빌드한다:
#   docker build -t korean-war-frontend frontend

# 1단계: 의존성을 받고 next build로 standalone 결과물을 만든다
FROM node:24-slim AS build
WORKDIR /app
# package.json이 그대로면 npm ci 단계를 캐시에서 다시 쓴다
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# rewrites(/api 등)의 백엔드 주소는 빌드 때 결과물에 박힌다. compose 안의 백엔드 컨테이너 이름을 쓴다
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=$BACKEND_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# 2단계: standalone 서버와 정적 파일만 담아 실행한다
FROM node:24-slim
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
# 컨테이너 밖(Caddy)에서 접속할 수 있게 모든 주소에서 듣는다
ENV HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /app/.next/standalone ./
# standalone에는 .next/static과 public이 빠져 있어 따로 복사한다
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
# node 이미지에 들어 있는 root 아닌 사용자로 실행한다
USER node
EXPOSE 3000
CMD ["node", "server.js"]
