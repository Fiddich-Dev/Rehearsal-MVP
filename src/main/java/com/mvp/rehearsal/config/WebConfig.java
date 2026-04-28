package com.mvp.rehearsal.config;

import com.mvp.rehearsal.service.TtsService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TtsService ttsService;

    public WebConfig(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = ttsService.audioDir().toUri().toString();
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(location);
    }
}
