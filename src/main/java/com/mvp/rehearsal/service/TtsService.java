package com.mvp.rehearsal.service;

import com.mvp.rehearsal.dto.Line;
import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.dto.VoiceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TtsService {

    private static final String LANGUAGE_CODE = "ko";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String model;
    private final String voiceA;
    private final String voiceB;
    private final String baseUrl;
    private final Path audioDir;

    public TtsService(
            @Value("${app.tts.api-key:}") String apiKey,
            @Value("${app.tts.model:eleven_v3}") String model,
            @Value("${app.tts.voice-a:}") String voiceA,
            @Value("${app.tts.voice-b:}") String voiceB,
            @Value("${app.tts.base-url:https://api.elevenlabs.io}") String baseUrl,
            @Value("${app.audio.dir:../audio}") String audioDirPath
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.voiceA = voiceA;
        this.voiceB = voiceB;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.audioDir = Path.of(audioDirPath).toAbsolutePath().normalize();
    }

    public Path audioDir() {
        return audioDir;
    }

    /**
     * Generate (or reuse cached) TTS mp3 for the given line of the given script.
     * Returns the relative URL path the controller should expose.
     */
    public String synthesize(Line line, Script script) {
        if (line == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "line is required");
        }
        if (line.text() == null || line.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        if (line.character() == null || line.character().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character is required");
        }

        String voiceId = voiceFor(line.character(), script);
        String ttsText = (line.ttsText() == null || line.ttsText().isBlank()) ? line.text() : line.ttsText();
        if (!supportsAudioTags(model)) {
            ttsText = stripAudioTags(ttsText);
        }
        VoiceSettings settings = line.voiceSettings();

        String cacheKey = ttsText + "|" + voiceId + "|" + voiceSettingsKey(settings) + "|" + model;
        String fileName = buildFileName(line.text(), line.character(), line.index(), cacheKey);

        Path target;
        try {
            Files.createDirectories(audioDir);
            target = audioDir.resolve(fileName);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "audio dir not writable", e);
        }

        if (!Files.exists(target)) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "TTS_API_KEY not configured");
            }
            if (voiceId == null || voiceId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "TTS_VOICE_A / TTS_VOICE_B not configured");
            }
            byte[] audio = callElevenLabs(ttsText, voiceId, settings);
            try {
                Files.write(target, audio);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to save audio", e);
            }
        }
        return "/audio/" + fileName;
    }

    private String voiceFor(String character, Script script) {
        if (script == null) return voiceB;
        var mapped = script.characterVoices();
        if (mapped != null) {
            String id = mapped.get(character);
            if (id != null && !id.isBlank()) return id;
        }
        var characters = script.characters();
        int idx = characters.indexOf(character);
        if (idx < 0) return voiceB;
        return (idx % 2 == 0) ? voiceA : voiceB;
    }

    private byte[] callElevenLabs(String text, String voiceId, VoiceSettings settings) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);
            body.put("model_id", model);
            body.put("language_code", LANGUAGE_CODE);
            Map<String, Object> vs = voiceSettingsToMap(settings);
            if (vs != null) body.put("voice_settings", vs);

            String json = mapper.writeValueAsString(body);
            String url = baseUrl + "/v1/text-to-speech/" + URLEncoder.encode(voiceId, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("xi-api-key", apiKey)
                    .header("Accept", "audio/mpeg")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                String err = new String(res.body(), StandardCharsets.UTF_8);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "elevenlabs tts failed: " + res.statusCode() + " " + err);
            }
            return res.body();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "tts call interrupted", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "elevenlabs tts call failed", e);
        }
    }

    private static Map<String, Object> voiceSettingsToMap(VoiceSettings s) {
        if (s == null) return null;
        Map<String, Object> m = new HashMap<>();
        if (s.stability() != null) m.put("stability", s.stability());
        if (s.similarityBoost() != null) m.put("similarity_boost", s.similarityBoost());
        if (s.style() != null) m.put("style", s.style());
        if (s.speed() != null) m.put("speed", s.speed());
        return m.isEmpty() ? null : m;
    }

    private static boolean supportsAudioTags(String modelId) {
        if (modelId == null) return false;
        return modelId.toLowerCase().contains("v3");
    }

    private static String stripAudioTags(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replaceAll("\\[[^\\[\\]]{1,30}\\]", "").replaceAll("\\s{2,}", " ").trim();
    }

    private static String voiceSettingsKey(VoiceSettings s) {
        if (s == null) return "default";
        return "s=" + s.stability() + ",sb=" + s.similarityBoost()
                + ",st=" + s.style() + ",sp=" + s.speed();
    }

    private static String buildFileName(String rawText, String character, int idx, String cacheKey) {
        String hash = shortHash(character + "::" + rawText + "::" + cacheKey);
        String safeChar = character.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        return "line_" + idx + "_" + safeChar + "_" + hash + ".mp3";
    }

    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
