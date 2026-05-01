package com.mvp.rehearsal.dto;

public record Line(
        int index,
        String character,
        String text,
        String ttsText,
        String beatGoal,
        String subtext,
        VoiceSettings voiceSettings,
        String audioUrl
) {
    public Line(int index, String character, String text) {
        this(index, character, text, null, null, null, null, null);
    }

    public Line(int index, String character, String text, String ttsText,
                String beatGoal, String subtext, VoiceSettings voiceSettings) {
        this(index, character, text, ttsText, beatGoal, subtext, voiceSettings, null);
    }
}
