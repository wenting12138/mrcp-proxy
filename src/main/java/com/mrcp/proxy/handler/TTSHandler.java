package com.mrcp.proxy.handler;

import com.mrcp.proxy.protocol.SynthesisMessage;

public interface TTSHandler {

    void onServerText(SynthesisMessage event) throws Exception;

}
