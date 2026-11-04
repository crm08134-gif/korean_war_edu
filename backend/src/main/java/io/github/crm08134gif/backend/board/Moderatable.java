package io.github.crm08134gif.backend.board;

// 신고·관리자 처리 대상: 글과 댓글. 같은 규칙으로 가리고 되살리려고 하나로 묶는다
public interface Moderatable {

    PostStatus getStatus();

    boolean isDeleted();

    boolean isWrittenBy(Long memberId);

    void blind();

    void restore();
}
