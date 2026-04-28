package com.mvp.rehearsal.dto;

public record Line(
        int index,
        String character,
        String text,
        String ttsText,
        String beatGoal,
        String subtext,
        VoiceSettings voiceSettings
) {
    public Line(int index, String character, String text) {
        this(index, character, text, null, null, null, null);
    }
}
