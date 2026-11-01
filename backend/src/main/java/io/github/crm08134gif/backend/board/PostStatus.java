package io.github.crm08134gif.backend.board;

// 글·댓글이 함께 쓴다 (V12의 CHECK와 같은 세 값)
public enum PostStatus {
    VISIBLE,  // 보임
    BLINDED,  // 신고 2명으로 가려짐 (4단계)
    DELETED   // 작성자가 지움
}
