# 운영 배포 (AWS Lightsail 서울, Docker Compose)

```
인터넷 ─443→ caddy ─→ frontend(Next, 3000) ─/api·/photos·/oauth2→ backend(8080) ─→ db(PostGIS)
```

- 이미지는 main에 머지될 때 GitHub Actions(`.github/workflows/images.yml`)가 만들어 `ghcr.io/crm08134-gif/`에 올린다. 서버는 받아서 실행만 한다.
- 바깥에 여는 포트는 Caddy의 80·443뿐이다. DB·백엔드·프론트는 compose 안에서만 서로 본다.
- 서버에 두는 파일: 이 폴더의 `compose.prod.yaml`, `Caddyfile`, 그리고 서버에서 새로 만든 `.env`.

## 처음 한 번

1. ghcr.io 패키지 두 개(`korean-war-backend`, `korean-war-frontend`)를 공개(Public)로 바꾼다.
   GitHub 프로필 → Packages → 패키지 → Package settings → Change visibility. 이미지에는 비밀이 없다(키는 `.env`에만).
2. Lightsail 서울(ap-northeast-2), Ubuntu 24.04, $12(2GB) 번들. 고정 IP를 붙이고 방화벽은 22·80·443만.
3. 도메인 DNS에 A 레코드 `@` → 고정 IP.
4. 서버(SSH)에서:

   ```bash
   # Docker 설치 (공식 스크립트)
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER   # 다시 로그인하면 sudo 없이 docker

   # 스왑 2GB (2GB 메모리에 Java·Node·PostgreSQL을 함께 돌리므로)
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

   # 배포 폴더
   mkdir -p ~/korean-war && cd ~/korean-war
   # compose.prod.yaml, Caddyfile, .env.example을 이 폴더로 받는다
   cp .env.example .env && nano .env   # 값 채우기 (POSTGRES_PASSWORD, JWT_SECRET는 새로 만든다)
   chmod 600 .env
   ```

5. 실행: `docker compose -f compose.prod.yaml pull && docker compose -f compose.prod.yaml up -d`
6. 카카오·구글 콘솔에 돌아올 주소 추가:
   `https://koreanwar-edu.tech/login/oauth2/code/kakao`, `https://koreanwar-edu.tech/login/oauth2/code/google`
7. 첫 관리자: 로그인·닉네임을 정한 뒤
   `docker compose -f compose.prod.yaml exec db psql -U warmap -d warmap -c "UPDATE member SET role='ADMIN' WHERE nickname='닉네임';"`

## 새 버전 올리기

main에 머지 → Actions의 Images가 끝나면 서버에서 같은 5번 명령을 다시 실행한다.

## 백업 (수동)

```bash
docker compose -f compose.prod.yaml exec -T db pg_dump -U warmap warmap | gzip > backup-$(date +%F).sql.gz
```

받은 파일은 내 PC로 옮겨 둔다. 서버를 지우기 전에 꼭 한다.
