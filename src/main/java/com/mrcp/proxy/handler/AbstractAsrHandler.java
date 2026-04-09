package com.mrcp.proxy.handler;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public abstract class AbstractAsrHandler implements AsrHandler, ClientCallBack {

    protected final Channel serverChannel;
    protected final String url;
    protected JSONObject event;
    protected WebSocketClient client;
    public AbstractAsrHandler(Channel serverChannel, String url) {
        this.serverChannel = serverChannel;
        this.url = url;
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        this.event = event;
        this.client = this.getClient();
    }

    @Override
    public void onServerBinary(ByteBuf buf) {
//        log.info("mrcp server音频数据包 -> 转发给asr服务器");
        ByteBuf byteBuf = Unpooled.directBuffer(buf.capacity());
        byteBuf.writeBytes(buf);

        client.sendBinary(byteBuf);
    }

    protected WebSocketClient getClient() throws Exception {
        return new WebSocketClient("asr", new URI(url), this);
    }

    @Override
    public void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    public abstract void onClientText(Channel channel, String text);

    public void onClientBinary(Channel channel, ByteBuf buf) {

    }

}
