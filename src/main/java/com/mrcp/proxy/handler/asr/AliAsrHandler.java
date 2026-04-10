package com.mrcp.proxy.handler.asr;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractAsrHandler;
import com.mrcp.proxy.ws.AsrConfig;
import com.mrcp.proxy.ws.client.WebSocketClient;
import com.mrcp.proxy.ws.client.ali.AliAsrClient;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AliAsrHandler extends AbstractAsrHandler {

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;

    public AliAsrHandler(Channel serverChannel, AsrConfig config) {
        super(serverChannel, config);
        this.appKey = config.getAsrProperties().get("appKey");
        this.accessKeyId = config.getAsrProperties().get("accessKeyId");
        this.accessKeySecret = config.getAsrProperties().get("accessKeySecret");
    }

    @Override
    protected String buildConnectRequest(String sessionId) {
        return "";
    }

    @Override
    protected AsrResult parseResult(String text) {
        if ("EOS".equals(text) || (text != null && text.startsWith("ALI_FAIL:"))) {
            return null;
        }
        JSONObject json = JSONObject.parseObject(text);
        String result = json.getString("result");
        boolean isFinal = json.getBooleanValue("isFinal");
        return new AsrResult(result, isFinal);
    }

    @Override
    protected WebSocketClient getClient() throws Exception {
        return new AliAsrClient(appKey, accessKeyId, accessKeySecret, this);
    }
}
