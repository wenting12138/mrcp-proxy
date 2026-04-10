package com.mrcp.proxy.handler.tts;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractTTSHandler;
import com.mrcp.proxy.ws.TtsConfig;
import com.mrcp.proxy.ws.client.WebSocketClient;
import com.mrcp.proxy.ws.client.ali.AliTtsClient;
import io.netty.channel.Channel;

public class AliTtsHandler extends AbstractTTSHandler {

    public final String appKey;
    public final String accessKeyId;
    public final String accessKeySecret;

    public AliTtsHandler(Channel serverChannel, TtsConfig config) {
        super(serverChannel, config);
        this.appKey = config.getTtsProperties().get("appKey");
        this.accessKeyId = config.getTtsProperties().get("accessKeyId");
        this.accessKeySecret = config.getTtsProperties().get("accessKeySecret");
    }

    @Override
    protected String buildSynthesisRequest(String text) {
        JSONObject req = new JSONObject();
        req.put("text", text);
        req.put("format", "pcm");
        req.put("sampleRate", 8000);
        req.put("voice", "aixia");
        req.put("pitchRate", 0);
        req.put("speechRate", 0);
        req.put("enable_subtitle", false);
        return req.toJSONString();
    }

    @Override
    protected WebSocketClient getClient() throws Exception {
        return new AliTtsClient(
                appKey,
                accessKeyId,
                accessKeySecret,
                this
        );
    }

    @Override
    protected boolean isSynthesisComplete(String text) {
        return text.startsWith("ALI_FAIL:") || "EOS".equals(text);
    }

}
