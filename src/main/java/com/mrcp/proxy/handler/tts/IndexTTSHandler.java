package com.mrcp.proxy.handler.tts;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractTTSHandler;
import com.mrcp.proxy.ws.TtsConfig;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IndexTTSHandler extends AbstractTTSHandler {

    public IndexTTSHandler(Channel serverChannel, TtsConfig config) {
        super(serverChannel, config);
    }

    @Override
    protected String buildSynthesisRequest(String text) {
        JSONObject req = new JSONObject();
        req.put("text", text);
        req.put("sampleRate", 8000);
        req.put("voiceName", "aixia");
        req.put("voiceVolume", 7);
        req.put("accessKeyId", "");
        req.put("accessKeySecret", "");
        req.put("appKey", "");
        return req.toJSONString();
    }

    @Override
    protected boolean isSynthesisComplete(String text) {
        return "EOS".equals(text);
    }

}
