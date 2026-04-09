package com.mrcp.proxy.ws.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public interface ClientCallBack {

    void onClientText(Channel channel, String text);

    void onClientBinary(Channel channel, ByteBuf buf);

}
