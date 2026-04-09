package com.mrcp.proxy.handler.asr;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractAsrHandler;
import com.mrcp.proxy.ws.AsrConfig;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunasrHandler extends AbstractAsrHandler {

    public FunasrHandler(Channel serverChannel, AsrConfig config) {
        super(serverChannel, config);
    }

    @Override
    protected String buildConnectRequest(String sessionId) {
        JSONObject req = new JSONObject();
        req.put("mode", "online");
        req.put("wav_name", sessionId);
        req.put("is_speaking", true);
        req.put("audio_fs", 8000);
        req.put("wav_format", "pcm");
        req.put("chunk_size", new int[]{5, 10, 5});
        return req.toJSONString();
    }

    @Override
    protected AsrResult parseResult(String text) {
        JSONObject jsonObject = JSONObject.parseObject(text);
        String mode = jsonObject.getString("mode");
        String result = jsonObject.getString("text");
        boolean isFinal = "2pass-offline".equals(mode);
        return new AsrResult(result, isFinal);
    }

}
