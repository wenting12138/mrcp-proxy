package com.mrcp.proxy.ws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "ws")
@Configuration
@Data
public class NettyConfig {

    private Integer port;

    private String wsPath;

    private String ttsUrl;

    private String asrUrl;

    private boolean asrAudioSaveEnabled = false;

    private String asrAudioSaveDir = "audio/asr";

    private boolean ttsAudioSaveEnabled = false;

    private String ttsAudioSaveDir = "audio/tts";

}
