package com.mrcp.proxy.handler.tts;

import com.mrcp.proxy.handler.TTSHandler;
import com.mrcp.proxy.ws.TtsConfig;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
@Component
public class TtsHandlerFactory {

    private final TtsConfig ttsConfig;

    private final Map<String, BiFunction<Channel, TtsConfig, TTSHandler>> registry = new HashMap<>();

    @Autowired
    public TtsHandlerFactory(TtsConfig ttsConfig) {
        this.ttsConfig = ttsConfig;
        register("indextts", IndexTTSHandler::new);
    }

    public void register(String name, BiFunction<Channel, TtsConfig, TTSHandler> creator) {
        registry.put(name.toLowerCase(), creator);
    }

    public TTSHandler create(Channel channel) {
        String handlerName = ttsConfig.getTtsHandler().toLowerCase();
        BiFunction<Channel, TtsConfig, TTSHandler> creator = registry.get(handlerName);
        if (creator == null) {
            throw new IllegalArgumentException("未知的TTS handler类型: " + handlerName
                    + ", 可用类型: " + registry.keySet());
        }
        log.info("创建TTS handler: {}", handlerName);
        return creator.apply(channel, ttsConfig);
    }

}
