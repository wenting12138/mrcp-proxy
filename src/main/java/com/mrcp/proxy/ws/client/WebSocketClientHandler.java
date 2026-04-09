package com.mrcp.proxy.ws.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
    private final String name;
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private final ClientCallBack clientCallBack;

    public WebSocketClientHandler(String name, WebSocketClientHandshaker handshaker, ClientCallBack clientCallBack) {
        this.handshaker = handshaker;
        this.clientCallBack = clientCallBack;
        this.name = name;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("{} WebSocket Client disconnected!", this.name);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("{} WebSocket Client handshake success!", this.name);
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                log.info("{} WebSocket Client handshake failed!", this.name);
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                "Unexpected FullHttpResponse (status=" + response.status() + ")");
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            log.info("{} Received Text: {}", this.name, textFrame.text());
            if (clientCallBack != null) {
                clientCallBack.onClientText(ch, textFrame.text());
            }
        }
        if (frame instanceof BinaryWebSocketFrame) {
            log.info("{} Received Binary frame of size: {}", this.name, frame.content().readableBytes());
            if (clientCallBack != null) {
                clientCallBack.onClientBinary(ch, frame.content());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }
}