# ADR-0008: 백엔드는 람다·스트림 대신 if·for문

- 상태: 채택
- 날짜: 2026-11-07 (2026-11-05 결정을 모아 적음)
- 관련: CLAUDE.md 3장, #63·#64

## 배경

운영자가 백엔드 코드를 나중에 한 줄씩 다시 공부할 예정이다. 스트림 체인과 메서드 참조는 줄마다 이유를 설명하기 어렵다.

## 결정

- `.stream().map().toList()`, `Collectors`, `anyMatch`, `::` 대신 `for`문. Optional은 `orElse(null)` 뒤 `if`로.
- 그대로 두는 것: Spring Security 람다 DSL(Security 7은 이 모양만 받는다), JwtConfig의 aud 람다, 화살표 switch, 한 줄 삼항, DTO record, SQL 텍스트 블록, 테스트의 `assertThatThrownBy(() -> ...)`.

## 검토한 대안

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| 스트림 그대로 | 짧다 | 줄마다 설명하기 어렵다 |

## 결과

- 코드가 길어지지만 읽고 고치기 쉽다. 새 코드도 같은 스타일로 쓴다.
