package com.mvp.rehearsal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.tts")
public class TtsVoicePool {

    private List<Voice> voices = new ArrayList<>();

    public List<Voice> getVoices() {
        return voices;
    }

    public void setVoices(List<Voice> voices) {
        this.voices = voices == null ? new ArrayList<>() : voices;
    }

    public boolean isEmpty() {
        return voices == null || voices.isEmpty();
    }

    public boolean containsId(String id) {
        if (id == null) return false;
        for (Voice v : voices) {
            if (id.equals(v.getId())) return true;
        }
        return false;
    }

    public static class Voice {
        private String id;
        private String name;
        private String description;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
