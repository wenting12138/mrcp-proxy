package com.mrcp.proxy.handler;


import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public abstract class AbstractTTSHandler implements TTSHandler, ClientCallBack {

    protected final Channel serverChannel;
    protected final String url;
    protected JSONObject event;
    protected WebSocketClient client;
    public AbstractTTSHandler(Channel serverChannel, String url) {
        this.serverChannel = serverChannel;
        this.url = url;
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        this.event = event;
        this.client = this.getClient();
    }

    protected WebSocketClient getClient() throws Exception {
        return new WebSocketClient("tts", new URI(url), this);
    }

    public abstract void onClientText(Channel channel, String text);

    @Override
    public void onClientBinary(Channel channel, ByteBuf msg){
//        log.info("tts音频数据 -> 转发给 mrcp server");
        ByteBuf byteBuf = Unpooled.directBuffer(msg.capacity());
        byteBuf.writeBytes(msg);
        serverChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
    }

}
