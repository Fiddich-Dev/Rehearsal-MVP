package com.mvp.rehearsal.dto;

import java.util.List;
import java.util.Map;

public record Script(
        String title,
        List<String> characters,
        Map<String, String> characterVoices,
        List<Line> lines
) {
    public Script(String title, List<String> characters, List<Line> lines) {
        this(title, characters, Map.of(), lines);
    }
}
