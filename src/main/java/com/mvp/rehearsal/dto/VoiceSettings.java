package com.mvp.rehearsal.dto;

public record VoiceSettings(
        Double stability,
        Double similarityBoost,
        Double style,
        Double speed
) {
}
