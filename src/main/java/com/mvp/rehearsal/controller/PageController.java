package com.mvp.rehearsal.controller;

import com.mvp.rehearsal.config.TtsVoicePool;
import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.service.ActiveScriptStore;
import com.mvp.rehearsal.service.ScriptService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PageController {

    private final ScriptService scriptService;
    private final ActiveScriptStore activeScriptStore;
    private final TtsVoicePool voicePool;
    private final String ttsModel;
    private final String fallbackVoiceA;
    private final String fallbackVoiceB;

    public PageController(
            ScriptService scriptService,
            ActiveScriptStore activeScriptStore,
            TtsVoicePool voicePool,
            @Value("${app.tts.model:eleven_multilingual_v2}") String ttsModel,
            @Value("${app.tts.voice-a:}") String fallbackVoiceA,
            @Value("${app.tts.voice-b:}") String fallbackVoiceB
    ) {
        this.scriptService = scriptService;
        this.activeScriptStore = activeScriptStore;
        this.voicePool = voicePool;
        this.ttsModel = ttsModel;
        this.fallbackVoiceA = fallbackVoiceA;
        this.fallbackVoiceB = fallbackVoiceB;
    }

    @GetMapping("/")
    public String index(Model model) {
        var script = scriptService.getScript();
        String exampleText = script.lines().stream()
                .map(l -> l.character() + ": " + l.text())
                .collect(Collectors.joining("\n"));
        model.addAttribute("exampleText", exampleText);
        return "index";
    }

    @GetMapping("/role-select")
    public String roleSelect(HttpSession session, Model model) {
        Script script = activeScriptStore.get(session).orElse(null);
        if (script == null) {
            return "redirect:/";
        }
        model.addAttribute("script", script);
        return "role-select";
    }

    @GetMapping("/rehearsal")
    public String rehearsal(@RequestParam("role") String role, HttpSession session, Model model) {
        Script script = activeScriptStore.get(session).orElseGet(scriptService::getScript);
        if (!script.characters().contains(role)) {
            return "redirect:/";
        }
        model.addAttribute("script", script);
        model.addAttribute("userRole", role);
        model.addAttribute("ttsModel", ttsModel);
        model.addAttribute("voiceAssignments", buildVoiceAssignments(script));
        return "rehearsal";
    }

    private List<Map<String, String>> buildVoiceAssignments(Script script) {
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> mapping = script.characterVoices();
        List<String> characters = script.characters();
        for (int i = 0; i < characters.size(); i++) {
            String character = characters.get(i);
            String voiceId = mapping == null ? null : mapping.get(character);
            String source;
            if (voiceId != null && !voiceId.isBlank()) {
                source = "LLM 매핑";
            } else {
                voiceId = (i % 2 == 0) ? fallbackVoiceA : fallbackVoiceB;
                source = "fallback (voice-a/b)";
            }
            String voiceName = lookupVoiceName(voiceId);
            result.add(Map.of(
                    "character", character,
                    "voiceId", voiceId == null ? "" : voiceId,
                    "voiceName", voiceName,
                    "source", source
            ));
        }
        return result;
    }

    private String lookupVoiceName(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) return "(미설정)";
        if (voicePool != null) {
            for (TtsVoicePool.Voice v : voicePool.getVoices()) {
                if (voiceId.equals(v.getId())) {
                    return v.getName() == null ? voiceId : v.getName();
                }
            }
        }
        return voiceId;
    }
}
