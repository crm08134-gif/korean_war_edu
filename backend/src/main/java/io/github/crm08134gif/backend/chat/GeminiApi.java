package io.github.crm08134gif.backend.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// Gemini generateContent API의 요청·응답 모양. 필요한 칸만 적는다.
// 문서: https://ai.google.dev/gemini-api/docs/generate-content/text-generation
// 레코드 이름이 그대로 JSON 칸 이름이 된다 (systemInstruction, maxOutputTokens ...)
final class GeminiApi {

    private GeminiApi() {
    }

    // 보내는 것: 규칙(systemInstruction), 대화(contents), 답의 길이·생각 수준(generationConfig)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(Content systemInstruction, List<Content> contents, GenerationConfig generationConfig) {
    }

    // 말 하나. role은 "user" 또는 "model". systemInstruction에는 role이 없다(null이면 JSON에서 뺀다)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String role, List<Part> parts) {
    }

    // 글 조각. thought가 true인 조각은 AI의 생각 과정이라 답에 넣지 않는다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text, Boolean thought) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerationConfig(Integer maxOutputTokens, ThinkingConfig thinkingConfig) {
    }

    // "low": 오래 생각하지 않고 빨리 답한다. 무료 한도도 덜 쓴다
    record ThinkingConfig(String thinkingLevel) {
    }

    // 받는 것: 후보 답 목록. 보통 하나만 온다. 모르는 칸(사용량 등)은 무시한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) {
    }
}
