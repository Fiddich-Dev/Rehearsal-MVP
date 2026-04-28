package com.mvp.rehearsal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TtsRequest(
        String text,
        String character,
        @JsonProperty("line_index") Integer lineIndex
) {
}
