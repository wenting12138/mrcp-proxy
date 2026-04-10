package com.mrcp.proxy.ws.client;

import io.netty.buffer.ByteBuf;

public interface ClientCallBack {

    void onClientText(String text);

    void onClientBinary(ByteBuf buf);

}
