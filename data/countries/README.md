# 참전국·의료지원국

`countries.csv` 한 줄이 나라 하나다. 백엔드 `CountryCatalog`가 앱 시작 때 읽어 검사하고 `GET /api/countries`로 낸다(DB에 넣지 않는다). 순서는 파일 순서 그대로 화면에 나온다.

| 칸 | 뜻 |
| --- | --- |
| id | 소문자 두 글자 나라 코드 |
| name | 화면에 보일 이름 |
| group | `combat`(전투병 파견) 또는 `medical`(의료지원) |
| flag | `frontend/public/flags/<flag>.svg`. 아직 없으면 빈칸(화면에 이름 첫 글자) |
| personnel | 참전 연인원 |
| killed, casualties | 전사자, 사상자(전사·부상·실종·포로 합). 참전국만 |
| unit | 의료지원국의 지원 부대. 모르면 빈칸 |
| note | 덧붙일 한 줄 |
| status | `draft` 또는 `reviewed` (사건과 같은 규칙) |

숫자는 모두 국가보훈부 표에서 옮겼다(2026-11-05 확인).

- 참전 인원·지원 부대: [Size of War Participation](https://www.mpva.go.kr/english/contents.do?key=986) (원자료: 박동찬, 『통계로 본 6·25전쟁』, 국방부 군사편찬연구소, 2014)
- 전사자·사상자: [Casualties](https://www.mpva.go.kr/english/contents.do?key=987)
- 독일 병원 운영 기간: [UNC Contributors: Germany](https://www.unc.mil/Organization/Contributors/Germany)
