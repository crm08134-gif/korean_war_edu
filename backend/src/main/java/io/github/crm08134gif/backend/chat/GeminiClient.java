package io.github.crm08134gif.backend.chat;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 구글 Gemini API를 부른다. 키는 서버에만 있고 브라우저로 나가지 않는다
@Component
class GeminiClient {

    // 답의 최대 길이(토큰). 3~6문장 답에는 넉넉하다
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final RestClient rest;
    private final String apiKey;
    private final String model;

    GeminiClient(@Value("${chat.gemini-api-key}") String apiKey, @Value("${chat.model}") String model) {
        // 구글이 늦게 답하면 무한정 기다리지 않는다. 연결 5초, 답 30초
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    // .env에 키를 넣지 않았으면 질문 기능을 끈다
    boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 규칙과 대화를 보내고 답 글자만 돌려준다. 답이 비었으면 null
    String generate(String systemText, List<GeminiApi.Content> contents) {
        GeminiApi.Request request = new GeminiApi.Request(
                new GeminiApi.Content(null, List.of(new GeminiApi.Part(systemText, null))),
                contents,
                new GeminiApi.GenerationConfig(MAX_OUTPUT_TOKENS, new GeminiApi.ThinkingConfig("low")));

        GeminiApi.Response response = rest.post()
                // {model} 자리에 모델 이름이 들어간다. 예: /models/gemini-3.8-flash:generateContent
                .uri("/models/{model}:generateContent", model)
                // 키는 주소(?key=)가 아니라 머리글로 보낸다. 주소는 로그에 남을 수 있다
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiApi.Response.class);
        return answerText(response);
    }

    // 첫 번째 후보의 글 조각을 이어 붙인다. 생각 과정(thought) 조각은 뺀다
    private static String answerText(GeminiApi.Response response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        GeminiApi.Content content = response.candidates().get(0).content();
        if (content == null || content.parts() == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (GeminiApi.Part part : content.parts()) {
            if (part.text() != null && !Boolean.TRUE.equals(part.thought())) {
                text.append(part.text());
            }
        }
        String answer = text.toString().strip();
        return answer.isEmpty() ? null : answer;
    }
}
