package com.mvp.rehearsal.service;

import com.mvp.rehearsal.config.TtsVoicePool;
import com.mvp.rehearsal.dto.Line;
import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.dto.VoiceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScriptParserService {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String BASE_PROMPT = """
            연극 리허설용 대본 파싱. JSON으로만 응답.

            연기 원칙 (반드시 따를 것):
            - 과장된 낭독·내레이터 톤 금지. 실제 배우가 상대 배우와 호흡 맞추듯 자연스럽고 절제된 발화.
            - 평이한 일상 대사엔 audio tag를 넣지 않는 것이 더 자연스러움. 장면이 분명히 요구할 때만 사용.
            - 한 라인에 audio tag는 0~2개. 3개 이상 쌓으면 부자연스러움.
            - 같은 캐릭터라도 라인의 상황·감정·템포에 따라 voice_settings가 변동되어야 함. 모든 라인이 같은 값이면 안 됨.
            - 말줄임표(...), 쉼표는 호흡 신호이므로 text/tts_text 모두 보존.

            출력 형식:
            {
              "title": "<제목 또는 빈 문자열>",
              "characters": [{"name":"인물","voice_id":"<풀에서 고른 id>"}],
              "lines": [
                {"index":0,"character":"인물","text":"원본","tts_text":"[softly] 원본","voice_settings":{"stability":0.45,"style":0.25,"speed":0.97}}
              ]
            }

            파싱 규칙:
            - 인물명은 대본 표기 그대로. 지문/괄호 안 묘사는 text/tts_text 모두 제외(단 호흡 큐로만 활용).
            - 장면헤더(S#1, INT., EXT.) 제외. index는 0부터 순차.
            - text: raw 대사. 인라인 태그 없음.
            - tts_text: text 기반 + 필요시만 아래 audio tag. 평이한 라인은 text와 동일하게.

            audio tag 카테고리 (필요할 때만 1~2개):
            - 감정: [happy][sad][angry][excited][nervous][scared][frustrated][annoyed][calm][curious][tired][tense][confused][surprised][embarrassed][disappointed][worried][amused][thoughtful][mischievously][sarcastic]
            - 발화 방식: [whispers][softly][shouts][murmuring][mumbles][shyly]
            - 비언어 반응: [laughs][chuckles][scoffs][sighs][exhales][gasps][gulps][clears throat][groans][whimpers]
            - 호흡·끊김: [hesitating][stammers][trails off]

            voice_settings (연극 친화 범위):
            - stability:
              · 격정·울먹임·울분 → 0.30~0.45 (낮을수록 감정 폭 ↑)
              · 일상 대화·평이 → 0.45~0.60
              · 차분한 내면 독백·진중한 어른 → 0.55~0.70
              · 0.75 이상은 내레이터 톤이라 연극엔 피함
            - style:
              · 캐릭터 색 강한 인물·감정선 도드라진 라인 → 0.25~0.45
              · 일상적·중립 → 0.0~0.2
              · 0.6 이상은 과장돼 들림
            - speed:
              · 흥분·기쁨·다급함·짜증 → 1.0~1.08
              · 평이한 일상 → 0.95~1.0
              · 무거움·슬픔·고민·여운 → 0.88~0.95
            - 모르는 필드는 생략.

            JSON 외 텍스트 절대 금지.
            """;

    private static final String VOICE_POOL_INSTRUCTION = """

            voice 풀 (이 안에서만 voice_id 선택, 풀 밖 id 금지, 캐릭터당 다른 voice 우선):
            %s
            """;

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String apiKey;
    private final String chatModel;
    private final TtsVoicePool voicePool;

    public ScriptParserService(
            @Value("${app.openai.api-key:}") String apiKey,
            @Value("${app.openai.chat-model:gpt-5-nano}") String chatModel,
            TtsVoicePool voicePool
    ) {
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.voicePool = voicePool;
    }

    public Script parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "script text is empty");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY not configured");
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String body = mapper.writeValueAsString(Map.of(
                    "model", chatModel,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", text)
                    )
            ));
            HttpRequest req = HttpRequest.newBuilder(URI.create(OPENAI_CHAT_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "openai chat failed: " + res.statusCode() + " " + res.body());
            }
            JsonNode root = mapper.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJsonContent(content);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "llm call interrupted", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "openai chat call failed", e);
        }
    }

    private String buildSystemPrompt() {
        if (voicePool == null || voicePool.isEmpty()) return BASE_PROMPT;
        try {
            List<Map<String, String>> poolJson = new ArrayList<>();
            for (TtsVoicePool.Voice v : voicePool.getVoices()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("id", v.getId() == null ? "" : v.getId());
                if (v.getName() != null) entry.put("name", v.getName());
                if (v.getDescription() != null) entry.put("description", v.getDescription());
                poolJson.add(entry);
            }
            String poolStr = mapper.writeValueAsString(poolJson);
            return BASE_PROMPT + String.format(VOICE_POOL_INSTRUCTION, poolStr);
        } catch (Exception e) {
            return BASE_PROMPT;
        }
    }

    private Script parseJsonContent(String content) {
        try {
            JsonNode node = mapper.readTree(content);
            String title = node.path("title").asText("");

            List<String> characters = new ArrayList<>();
            Map<String, String> characterVoices = new HashMap<>();
            JsonNode charsNode = node.path("characters");
            for (JsonNode c : charsNode) {
                String name;
                String voiceId = "";
                if (c.isObject()) {
                    name = c.path("name").asText("");
                    voiceId = c.path("voice_id").asText("");
                } else {
                    name = c.asText("");
                }
                if (name.isBlank()) continue;
                characters.add(name);
                if (!voiceId.isBlank() && voicePool != null && voicePool.containsId(voiceId)) {
                    characterVoices.put(name, voiceId);
                }
            }

            List<Line> lines = new ArrayList<>();
            int idx = 0;
            for (JsonNode l : node.path("lines")) {
                String character = l.path("character").asText("");
                String text = l.path("text").asText("");
                if (character.isBlank() || text.isBlank()) continue;
                String ttsText = nullIfBlank(l.path("tts_text").asText(""));
                VoiceSettings vs = parseVoiceSettings(l.path("voice_settings"));
                lines.add(new Line(idx++, character, text, ttsText, null, null, vs));
            }
            if (characters.isEmpty() || lines.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "could not extract characters/lines from script");
            }
            return new Script(
                    title.isBlank() ? "사용자 대본" : title,
                    characters,
                    characterVoices,
                    lines
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "llm returned invalid JSON: " + e.getMessage(), e);
        }
    }

    private VoiceSettings parseVoiceSettings(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) return null;
        Double stability = optDouble(node, "stability");
        Double similarityBoost = optDouble(node, "similarity_boost");
        Double style = optDouble(node, "style");
        Double speed = optDouble(node, "speed");
        if (stability == null && similarityBoost == null && style == null && speed == null) {
            return null;
        }
        return new VoiceSettings(stability, similarityBoost, style, speed);
    }

    private static Double optDouble(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) return null;
        return v.asDouble();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
