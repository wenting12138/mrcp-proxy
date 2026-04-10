package com.mrcp.proxy.ws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "asr")
@Configuration
@Data
public class AsrConfig {

    private String asrHandler = "funasr";

    private String asrUrl;

    private boolean asrAudioSaveEnabled = false;

    private String asrAudioSaveDir = "audio/asr";

    private Map<String, String> asrProperties = new HashMap<>();
}
