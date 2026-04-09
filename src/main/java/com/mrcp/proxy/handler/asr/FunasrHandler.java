package com.mrcp.proxy.handler.asr;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.AbstractAsrHandler;
import com.mrcp.proxy.utils.MrcpTTSMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunasrHandler extends AbstractAsrHandler {

    public FunasrHandler(Channel serverChannel, String url) {
        super(serverChannel, url);
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        super.onServerText(event);
        String name = event.getJSONObject("header").getString("name");
        if ("StartTranscription".equalsIgnoreCase(name)) {
            client.connect();
            String sessionId = event.getJSONObject("context").getString("session_id");
            client.sendText(this.buildRequest(sessionId));
            serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildAsrStartMessage(event)));
        }
    }

    @Override
    public void onClientText(Channel channel, String result) {
        // {"is_final":false,"mode":"2pass-offline","text":"喂喂喂","wav_name":"b6bf7aeff3e54a64"}
        JSONObject jsonObject = JSONObject.parseObject(result);
        String mode = jsonObject.getString("mode");
        if ("2pass-offline".equals(mode)) {
            String text = jsonObject.getString("text");
            log.info("funasr recog result: {}", text);
            serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildAsrResultMessage(event, text)));
        }else {
            String text = jsonObject.getString("text");
            serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildAsrResultChangedMessage(event, text)));
        }
    }

    public String buildRequest(String sessionId){
        JSONObject req = new JSONObject();
        req.put("mode", "online");
        req.put("wav_name", sessionId);
        req.put("is_speaking", true);
        req.put("audio_fs", 8000);
        req.put("wav_format", "pcm");
        req.put("chunk_size", new int[]{5, 10, 5});
        return req.toJSONString();
    }

}
