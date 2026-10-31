package io.github.crm08134gif.backend.member;

// 프론트가 로그인 상태를 알기 위한 응답. 회원 번호나 제공자 정보는 내보내지 않는다
record MeResponse(boolean loggedIn, String nickname, boolean needsNickname, String role) {

    static MeResponse anonymous() {
        return new MeResponse(false, null, false, null);
    }

    static MeResponse of(Member member) {
        return new MeResponse(true, member.getNickname(), member.needsNickname(), member.getRole().name());
    }
}
