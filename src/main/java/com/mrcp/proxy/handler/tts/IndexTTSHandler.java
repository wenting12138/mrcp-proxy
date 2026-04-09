package com.mrcp.proxy.handler.tts;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractTTSHandler;
import com.mrcp.proxy.utils.MrcpTTSMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class IndexTTSHandler extends AbstractTTSHandler {

    public IndexTTSHandler(Channel serverChannel, String url) {
        super(serverChannel, url);
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        super.onServerText(event);
        String name = event.getJSONObject("header").getString("name");
        if ("StartSynthesis".equalsIgnoreCase(name)) {
            client.connect();
            String text = event.getJSONObject("payload").getString("text");
            client.sendText(this.buildRequest(text));
            serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildTtsStartMessage(event)));
        }
    }

    @Override
    public void onClientText(Channel channel, String text) {
        if ("EOS".equals(text)) {
            client.close();
            serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildTtsCompleteMessage(event)));
            serverChannel.close();
        }
    }

    public String buildRequest(String text){
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

}
