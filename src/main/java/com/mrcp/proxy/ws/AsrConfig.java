package com.mrcp.proxy.ws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "asr")
@Configuration
@Data
public class AsrConfig {

    private String asrHandler = "funasr";

    private String asrUrl;

    private boolean asrAudioSaveEnabled = false;

    private String asrAudioSaveDir = "audio/asr";
}
