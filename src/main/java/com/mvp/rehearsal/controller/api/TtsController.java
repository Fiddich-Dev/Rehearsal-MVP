package com.mvp.rehearsal.controller.api;

import com.mvp.rehearsal.dto.Line;
import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.dto.TtsRequest;
import com.mvp.rehearsal.dto.TtsResponse;
import com.mvp.rehearsal.service.ActiveScriptStore;
import com.mvp.rehearsal.service.ScriptService;
import com.mvp.rehearsal.service.TtsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class TtsController {

    private final TtsService ttsService;
    private final ActiveScriptStore activeScriptStore;
    private final ScriptService scriptService;

    public TtsController(
            TtsService ttsService,
            ActiveScriptStore activeScriptStore,
            ScriptService scriptService
    ) {
        this.ttsService = ttsService;
        this.activeScriptStore = activeScriptStore;
        this.scriptService = scriptService;
    }

    @PostMapping("/tts")
    public TtsResponse tts(@RequestBody TtsRequest req, HttpSession session) {
        Script script = activeScriptStore.get(session).orElseGet(scriptService::getScript);
        Line line = resolveLine(script, req);
        String url = ttsService.synthesize(line, script);
        return new TtsResponse(url);
    }

    private Line resolveLine(Script script, TtsRequest req) {
        Integer idx = req.lineIndex();
        if (idx != null && idx >= 0 && idx < script.lines().size()) {
            Line stored = script.lines().get(idx);
            if (matchesRequest(stored, req)) {
                return stored;
            }
        }
        if (req.text() == null || req.text().isBlank() || req.character() == null || req.character().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text/character required when line not in active script");
        }
        return new Line(idx == null ? 0 : idx, req.character(), req.text());
    }

    private static boolean matchesRequest(Line line, TtsRequest req) {
        if (req.character() != null && !req.character().equals(line.character())) return false;
        if (req.text() != null && !req.text().equals(line.text())) return false;
        return true;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException e) {
        String reason = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body(reason == null ? "" : reason);
    }
}
