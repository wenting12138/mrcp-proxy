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
    private final String type;
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private final ClientCallBack clientCallBack;

    public WebSocketClientHandler(String type, WebSocketClientHandshaker handshaker, ClientCallBack clientCallBack) {
        this.handshaker = handshaker;
        this.clientCallBack = clientCallBack;
        this.type = type;
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
        log.info("{} WebSocket Client disconnected!", this.type);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("{} WebSocket Client handshake success!", this.type);
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                log.info("{} WebSocket Client handshake failed!", this.type);
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
            log.info("{} Received Text: {}", this.type, textFrame.text());
            if (clientCallBack != null) {
                clientCallBack.onClientText(textFrame.text());
            }
        }
        if (frame instanceof BinaryWebSocketFrame) {
            log.info("{} Received Binary frame of size: {}", this.type, frame.content().readableBytes());
            if (clientCallBack != null) {
                clientCallBack.onClientBinary(frame.content());
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