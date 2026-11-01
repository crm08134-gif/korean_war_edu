package io.github.crm08134gif.backend.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record NicknameRequest (
    // 한글 완성형·영문·숫자만 2~12자. 공백, 특수문자, 보이지 않는 글자, 다른 문자의 비슷한 모양 글자를 막는다
    @NotNull
    @Pattern(regexp = "[가-힣a-zA-Z0-9]{2,12}", message = "닉네임은 한글.영문.숫자 2~12자로 써주세요")
    String nickname) {
}
