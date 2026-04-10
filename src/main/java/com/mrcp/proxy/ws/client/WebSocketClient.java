package com.mrcp.proxy.ws.client;

import io.netty.buffer.ByteBuf;

import java.net.URI;

/**
 * 上游厂商 WebSocket 客户端抽象，便于对接不同 SDK 实现。
 */
public interface WebSocketClient {

    void connect(String uri) throws Exception;

    void sendText(String text);

    void sendBinary(ByteBuf buf);

    void close();
}
