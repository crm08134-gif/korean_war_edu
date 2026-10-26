# 사건 카드 사진

사건 카드의 대표 사진과 본문 사진. **허용 목록의 라이선스만 쓴다** (CLAUDE.md 0장 "사진·그림·지도 이미지"). 애매하면 넣지 않고 사용자에게 묻는다.

## 파일 구조

```
data/photos/
├─ photos.csv           사진 하나에 한 줄 (메타데이터)
└─ <파일 이름>.jpg       줄인 사진 (scripts/photos/resize.py)
```

### `photos.csv`

| 열 | 필수 | 내용 |
| --- | --- | --- |
| `file` | 예 | 이 폴더의 파일 이름 (영문 소문자, 숫자, `-`, `.jpg`) |
| `title` | 예 | 원본의 제목. 화면에서 원본 페이지로 이어지는 링크 글자가 된다. 공공누리는 저작물명 그대로 |
| `author` | 예 | 작성자·기관. 예: `미 육군 통신대 / NARA` |
| `source_url` | 예 | 원본 페이지 (커먼즈 파일 페이지, NARA 카탈로그 등). 검색 결과나 재게시본이 아니라 라이선스를 확인한 그 페이지 |
| `license` | 예 | 아래 값 중 하나 |
| `license_url` | 퍼블릭 도메인이 아니면 예 | 라이선스 본문 주소 |
| `retrieved_on` | 예 | 받은 날짜 `YYYY-MM-DD` |
| `modified` | | 바꾼 것. 예: `크기 조정`, `자르기·크기 조정`. CC BY-SA는 바꿨으면 꼭 적는다 |

`license` 값:

| 값 | 화면 표기 |
| --- | --- |
| `public-domain` | 퍼블릭 도메인 |
| `cc0` | CC0 |
| `cc-by-2.0` · `cc-by-2.5` · `cc-by-3.0` · `cc-by-4.0` | CC BY 2.0 … 4.0 |
| `cc-by-sa-2.0` · `cc-by-sa-2.5` · `cc-by-sa-3.0` · `cc-by-sa-4.0` | CC BY-SA 2.0 … 4.0 |
| `kogl-1` | 공공누리 제1유형 |

사건 파일에서는 대표 사진을 `"photo": {"file": "…", "caption": "…"}`로, 본문 사진을 `{"type": "photo", "file": "…", "caption": "…"}` 블록으로 쓴다 ([data/events/README.md](../events/README.md)). 화면에는 사진 바로 아래에 설명과 `사진: 작성자 · 제목(원본 링크) · 라이선스 · 바꾼 것`이 작은 글씨로 나온다.

## 사진 넣는 순서

1. 원본 페이지를 직접 열어 라이선스를 확인한다. 커먼즈는 라이선스 틀(예: PD-USGov-Military-Army)과 Source·Author가 맞는지 본다. 미군 사진이라면서 출처가 통신사(AP, Getty, LIFE 등)면 쓰지 않는다.
2. 시신이나 심한 부상이 드러난 사진, 실존 민간인의 얼굴이 크게 나온 사진은 쓰지 않는다.
3. 원본을 받아 줄인다(긴 변 1200px, JPEG). 원본은 저장소에 넣지 않는다.

   ```bash
   python scripts/photos/resize.py <원본> data/photos/<파일 이름>.jpg
   ```

4. `photos.csv`에 한 줄을 적는다. 줄였으면 `modified`에 `크기 조정`.

## 앱에 들어가는 방식

빌드할 때 `photos.csv`는 jar 안 `photos/`로, 사진 파일은 `static/photos/`로 복사된다(`backend/build.gradle`). 앱이 켜질 때 `EventLoader`가 DB `photo`를 다시 채우고, 사진은 `/photos/<파일 이름>`으로 내려받는다(프론트는 Next가 백엔드로 넘긴다).

아래 중 하나라도 걸리면 앱이 뜨지 않는다.

- 적재기: `photos.csv`에 없는 사진 파일이 있다, `photos.csv`에 있는데 파일이 없다
- DB: 허용 목록 밖의 `license`, 퍼블릭 도메인이 아닌데 `license_url`이 없다, 빠진 필수 값, 사건이 `photos.csv`에 없는 사진을 쓴다, 한 사건에 같은 사진을 두 번 쓴다, 대표 사진에 설명이 없다
