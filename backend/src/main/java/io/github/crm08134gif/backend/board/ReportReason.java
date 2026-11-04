package io.github.crm08134gif.backend.board;

// 신고 사유. 운영 정책의 금지 항목과 같다 (V13 report_reason_check)
public enum ReportReason {
    HATE,        // 특정 집단(지역·민족·국적·성별·이념 지지층)에 대한 혐오·비하
    DEFAMATION,  // 실존 인물·유족에 대한 근거 없는 비방
    MOCKERY,     // 학살·전쟁범죄 피해의 조롱이나 정당화
    PRIVACY,     // 개인정보 노출
    SPAM,        // 광고·도배
    OTHER        // 기타 (이유를 꼭 적는다. 가려진 데 대한 재검토 요청도 여기로)
}
