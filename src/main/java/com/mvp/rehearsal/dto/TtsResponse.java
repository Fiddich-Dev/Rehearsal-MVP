package com.mvp.rehearsal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TtsResponse(@JsonProperty("audio_url") String audioUrl) {
}
