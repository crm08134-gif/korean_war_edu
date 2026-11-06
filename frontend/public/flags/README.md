# 당시 국기

관련 인물 이름 옆에 붙는 1950년대 국기. 파일 이름은 `data/people/people.csv`의 `country` 값이다.
모두 위키미디어 커먼즈의 퍼블릭 도메인 그림이다(2026-10-30 확인). su.svg는 화면 크기에서 차이가 없도록 좌표를 소수 한 자리로 줄이고 편집기 정보를 지웠다.

| 파일 | 나라 | 커먼즈 원본 |
| --- | --- | --- |
| kr.svg | 대한민국 (1949~1984년 국기) | [Flag of South Korea (1949–1984).svg](https://commons.wikimedia.org/wiki/File:Flag_of_South_Korea_(1949%E2%80%931984).svg) |
| kp.svg | 조선민주주의인민공화국 | [Flag of North Korea.svg](https://commons.wikimedia.org/wiki/File:Flag_of_North_Korea.svg) |
| us.svg | 미국 (별 48개, 1912~1959년) | [Flag of the United States (1912-1959).svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_United_States_(1912-1959).svg) |
| su.svg | 소련 (1936~1955년) | [Flag of the Soviet Union (1936 – 1955).svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_Soviet_Union_(1936_%E2%80%93_1955).svg) |
| cn.svg | 중화인민공화국 | [Flag of the People's Republic of China.svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_People%27s_Republic_of_China.svg) |
| gb.svg | 영국 | [Flag of the United Kingdom (3-5).svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_United_Kingdom_(3-5).svg) |
| fr.svg | 프랑스 | [Flag of France.svg](https://commons.wikimedia.org/wiki/File:Flag_of_France.svg) |
| au.svg | 오스트레일리아 | [Flag of Australia.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Australia.svg) |
| ca.svg | 캐나다 (Red Ensign, 1921~1957년) | [Flag of Canada (1921–1957).svg](https://commons.wikimedia.org/wiki/File:Flag_of_Canada_(1921%E2%80%931957).svg) |
| tr.svg | 튀르키예 | [Flag of Turkey.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Turkey.svg) |
| gr.svg | 그리스 (1822~1978년) | [Flag of Greece (1822-1978).svg](https://commons.wikimedia.org/wiki/File:Flag_of_Greece_(1822-1978).svg) |
| ph.svg | 필리핀 (1936~1985년) | [Flag of the Philippines (1936–1985, 1986–1998).svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_Philippines_(1936%E2%80%931985,_1986%E2%80%931998).svg) |
| th.svg | 태국 | [Flag of Thailand.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Thailand.svg) |
| nl.svg | 네덜란드 | [Flag of the Netherlands.svg](https://commons.wikimedia.org/wiki/File:Flag_of_the_Netherlands.svg) |
| co.svg | 콜롬비아 | [Flag of Colombia.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Colombia.svg) |
| nz.svg | 뉴질랜드 | [Flag of New Zealand.svg](https://commons.wikimedia.org/wiki/File:Flag_of_New_Zealand.svg) |
| et.svg | 에티오피아 (제국기, 1897~1974년) | [Flag of Ethiopia (1897–1974).svg](https://commons.wikimedia.org/wiki/File:Flag_of_Ethiopia_(1897%E2%80%931974).svg) |
| be.svg | 벨기에 (3:2 비율) | [Flag of Belgium (civil).svg](https://commons.wikimedia.org/wiki/File:Flag_of_Belgium_(civil).svg) |
| za.svg | 남아프리카공화국 (1928~1994년) | [Flag of South Africa (1928–1994, dark colors).svg](https://commons.wikimedia.org/wiki/File:Flag_of_South_Africa_(1928%E2%80%931994,_dark_colors).svg) |
| lu.svg | 룩셈부르크 | [Flag of Luxembourg.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Luxembourg.svg) |
| se.svg | 스웨덴 | [Flag of Sweden.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Sweden.svg) |
| dk.svg | 덴마크 | [Flag of Denmark.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Denmark.svg) |
| in.svg | 인도 | [Flag of India.svg](https://commons.wikimedia.org/wiki/File:Flag_of_India.svg) |
| no.svg | 노르웨이 | [Flag of Norway.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Norway.svg) |
| it.svg | 이탈리아 | [Flag of Italy.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Italy.svg) |
| de.svg | 독일 (서독, 1949년~) | [Flag of Germany.svg](https://commons.wikimedia.org/wiki/File:Flag_of_Germany.svg) |

참전국 화면(`data/countries/countries.csv`)의 국기 18개는 2026-11-06에 확인했다. ca.svg와 et.svg는 원본(115KB, 59KB)이 문장(紋章)까지 그린 큰 그림이라, 커먼즈가 만든 PNG 미리보기를 가로 108px로 줄여 SVG에 담았다(크기 조정). 나머지는 원본 그대로다.

새 나라를 더하면 여기 한 줄, `frontend/src/lib/people.ts`의 `COUNTRY_LABEL`에 한 줄을 더한다.
