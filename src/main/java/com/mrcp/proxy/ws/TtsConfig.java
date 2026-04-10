package com.mrcp.proxy.ws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "tts")
@Configuration
@Data
public class TtsConfig {

    private String ttsHandler = "indextts";

    private String ttsUrl;

    private boolean ttsAudioSaveEnabled = false;

    private String ttsAudioSaveDir = "audio/tts";

    private Map<String, String> ttsProperties = new HashMap<>();

}
