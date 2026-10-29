# 전쟁 속 이야기 데이터

헤더의 "이야기" 버튼으로 여는 글. 글과 사진 규칙은 [CLAUDE.md](../../CLAUDE.md) 0장과 5장(자료 예외)을 따른다.

## 이야기 파일 `<id>.json`

사건 파일([data/events/README.md](../events/README.md))에서 날짜·장소·좌표·종류를 뺀 모양이다. 본문 블록, 출처(`data/events/sources.csv`의 id), 사진(`data/photos/photos.csv`의 파일)은 사건과 같다.

```json
{
  "id": "example-story",
  "title": "이야기 제목",
  "summary": "요약 2~3문장. 목록에도 나온다",
  "photo": {"file": "example.jpg", "caption": "대표 사진 설명"},
  "body": [
    {"type": "heading", "text": "소제목"},
    {"type": "paragraph", "text": "문단"}
  ],
  "sources": [
    {"sourceId": "cmh-appleman", "locator": "5장"}
  ],
  "status": "draft"
}
```

(위 값은 모양을 보이려는 예시다.)

| 필드 | 내용 |
| --- | --- |
| `id` | 영문 소문자와 `-`. 파일 이름과 같아야 한다 |
| `summary` | 2~3문장. 목록에서는 제목 아래 두 줄까지만 보인다 |
| `photo` | 대표 사진. 없으면 `null`. 목록의 썸네일(16:9로 잘라 보임)과 카드 위쪽에 나온다. 썸네일 아래에도 출처가 붙는다 |
| `status` | 새로 넣으면 `draft`. 사용자가 검수한 뒤에만 `reviewed`로 바꾼다 |

목록은 제목 가나다순이다.

이 화면의 글은 공식 자료 외에 뉴스 기사도 사실 확인에 쓸 수 있다(CLAUDE.md 5장). 기사는 `data/events/sources.csv`에 `news-<매체>-<날짜>` id로 넣고, 제목 뒤에 기사 날짜를 적는다.
