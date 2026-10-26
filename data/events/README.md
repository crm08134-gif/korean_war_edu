# 사건 데이터

지도 마커와 사건 카드에 나오는 사건과 그 출처. 글을 쓰는 규칙(저작권, 자료와 관점, 카드 분량)은 [CLAUDE.md](../../CLAUDE.md) 0·1·4장을 따른다.

## 파일 구조

```
data/events/
├─ sources.csv                  출처 목록 (책·자료 하나에 한 줄)
├─ 1950-06-25-outbreak.json     사건 하나에 파일 하나: <date>-<id>.json
└─ …
```

### `sources.csv`

| 열 | 필수 | 내용 |
| --- | --- | --- |
| `id` | 예 | 사건 파일의 `sourceId`가 가리키는 이름 (예: `cmh-appleman`) |
| `title` | 예 | 자료 제목. 카드 출처 목록에 나온다 |
| `author` | | 저자. 기록용이고 카드에는 나오지 않는다 |
| `publisher` | 예 | 펴낸 기관. 카드 출처 목록 맨 앞에 나온다 (예: 미 육군 군사사 센터) |
| `url` | | 원본 주소. 있으면 카드의 제목이 이 주소로 이어진다 |
| `license` | 예 | 자료의 이용 조건 (예: Public domain (U.S. Government work), All rights reserved) |

UTF-8로 저장한다. 쉼표가 들어간 값은 큰따옴표로 감싼다.

### 사건 파일 `<date>-<id>.json`

```json
{
  "id": "osan",
  "title": "오산 죽미령 전투",
  "date": "1950-07-05",
  "datePrecision": "day",
  "placeName": "오산 죽미령",
  "lon": 127.0,
  "lat": 37.1,
  "kind": "battle",
  "summary": "요약 2~3문장",
  "body": [
    {"type": "heading", "text": "배경"},
    {"type": "paragraph", "text": "문단"},
    {"type": "table", "caption": "표 제목", "columns": ["구분", "미군", "북한군"], "rows": [["병력", "…", "…"]], "note": "표 아래 작은 글씨 (없으면 null)"}
  ],
  "sources": [
    {"sourceId": "cmh-appleman", "locator": "5장"}
  ],
  "status": "draft"
}
```

(위 값은 모양을 보이려는 예시다. 실제 좌표와 내용은 자료로 확인한 것만 쓴다.)

| 필드 | 내용 |
| --- | --- |
| `id` | 영문 소문자와 `-`. 파일 이름의 날짜 뒤 부분과 같아야 한다 |
| `date`, `datePrecision` | `YYYY-MM-DD`, `day` 또는 `month`(날짜를 모를 때. 화면에 "1950년 8월"로 나온다). 1950-06-25 ~ 1953-07-27 |
| `placeName`, `lon`, `lat` | 장소와 좌표. 좌표 출처는 CLAUDE.md 0장 "좌표". 정확한 지점이 없으면 가까운 도시를 쓰고 이름에 "(대표 위치)"를 붙인다 |
| `kind` | 종류. 지도 마커 아이콘을 고른다: `battle` 전투, `offensive` 공세·돌파, `capture` 점령·함락·수복, `landing` 상륙, `withdrawal` 철수, `talks` 회담·협정 |
| `summary` | 카드 위쪽 요약 2~3문장 |
| `body` | 본문 블록 배열. `heading`(소제목: 배경·경과·결과·의미), `paragraph`(문단), `table`(표) |
| `sources` | 출처 목록. 카드 맨 아래에 이 순서대로 "기관, 제목, 장·쪽" 한 줄씩 나온다 |
| `status` | 새로 쓴 사건은 `draft`. 사용자가 검수한 뒤 `reviewed`로 바꾼다. 화면에는 나오지 않는다 |

## 앱에 들어가는 방식

이 폴더가 기준이다. 앱이 켜질 때 `EventLoader`가 DB의 사건과 출처를 모두 지우고 이 폴더 내용으로 다시 넣는다. 백엔드는 `reviewed`만 내보내고, 환경 변수 `EVENTS_INCLUDE_DRAFTS=true`로 켜면 `draft`도 내보낸다.

아래 중 하나라도 걸리면 앱이 뜨지 않는다. 로그에 어느 파일인지 나온다.

- 적재기: 파일 이름이 `<date>-<id>.json`과 다르다, 모르는 필드나 블록 `type`이 있다, 본문이나 출처가 없다, 빈 소제목·문단이 있다, 표의 줄과 칸 이름의 칸 수가 다르다
- DB: 빠진 필수 값, 전쟁 기간 밖의 날짜, 한반도 밖의 좌표, 모르는 `kind`·`datePrecision`·`status`, `sources.csv`에 없는 `sourceId`, 같은 출처·장이 두 번
