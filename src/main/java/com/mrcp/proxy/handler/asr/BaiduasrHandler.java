package com.mrcp.proxy.handler.asr;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractAsrHandler;
import com.mrcp.proxy.ws.AsrConfig;
import com.mrcp.proxy.ws.NettyConfig;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaiduasrHandler extends AbstractAsrHandler {

    public BaiduasrHandler(Channel serverChannel, AsrConfig config) {
        super(serverChannel, config);
    }

    @Override
    protected String buildConnectRequest(String sessionId) {
        JSONObject req = new JSONObject();

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
