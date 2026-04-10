package com.mrcp.proxy.handler;

import com.mrcp.proxy.protocol.SynthesisMessage;

public interface TTSHandler {
    public static final String HANDLER_TYPE = "tts";
    void onServerText(SynthesisMessage event) throws Exception;

}
