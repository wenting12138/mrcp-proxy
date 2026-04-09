package com.mrcp.proxy.handler.asr;

import com.mrcp.proxy.handler.AsrHandler;
import com.mrcp.proxy.ws.AsrConfig;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
@Component
public class AsrHandlerFactory {

    private final AsrConfig asrConfig;

    private final Map<String, BiFunction<Channel, AsrConfig, AsrHandler>> registry = new HashMap<>();

    @Autowired
    public AsrHandlerFactory(AsrConfig asrConfig) {
        this.asrConfig = asrConfig;
        register("funasr", FunasrHandler::new);
    }

    public void register(String name, BiFunction<Channel, AsrConfig, AsrHandler> creator) {
        registry.put(name.toLowerCase(), creator);
    }

    public AsrHandler create(Channel channel) {
        String handlerName = asrConfig.getAsrHandler().toLowerCase();
        BiFunction<Channel, AsrConfig, AsrHandler> creator = registry.get(handlerName);
        if (creator == null) {
            throw new IllegalArgumentException("未知的ASR handler类型: " + handlerName
                    + ", 可用类型: " + registry.keySet());
        }
        log.info("创建ASR handler: {}", handlerName);
        return creator.apply(channel, asrConfig);
    }

}
