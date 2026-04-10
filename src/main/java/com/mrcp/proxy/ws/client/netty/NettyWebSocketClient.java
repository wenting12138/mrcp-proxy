package com.mrcp.proxy.ws.client.netty;

import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import com.mrcp.proxy.ws.client.WebSocketClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.net.URI;

@Slf4j
public class NettyWebSocketClient implements WebSocketClient {

    private final String type;
    private URI uri;
    private SslContext sslCtx;
    private Channel channel;
    private WebSocketClientHandshaker handshaker;
    private WebSocketClientHandler handler;
    private final ClientCallBack clientCallBack;

    public NettyWebSocketClient(String type, ClientCallBack clientCallBack) throws Exception {
        this.type = type;
        this.clientCallBack = clientCallBack;

    }

    @Override
    public void connect(String url) throws Exception {
        initCtx(url);
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort()));
                            }
                            p.addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    WebSocketClientCompressionHandler.INSTANCE,
                                    handler
                            );
                        }
                    });

            log.info("{} Connecting to {}:{}", this.type, uri.getHost(), uri.getPort());
            Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
            channel = ch;

            handler.handshakeFuture().sync();

            log.info("{} WebSocket Client connected!", this.type);
        } catch (Exception e) {
            group.shutdownGracefully();
            throw new Exception(type + " connect failed: " + uri, e);
        }
    }

    private void initCtx(String url) throws Exception {
        this.uri = new URI(url);
        boolean useSsl = "wss".equalsIgnoreCase(uri.getScheme());
        this.sslCtx = useSsl ? SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build() : null;
        this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri,
                WebSocketVersion.V13,
                null,
                true,
                new DefaultHttpHeaders(),
                10 * 1024 * 1024
        );
        this.handler = new WebSocketClientHandler(type, handshaker, this.clientCallBack);
    }

    @Override
    public void sendText(String text) {
        if (channel != null && channel.isActive()) {
            WebSocketFrame frame = new TextWebSocketFrame(text);
            channel.writeAndFlush(frame);
            log.info("{} Sent Client: {}", this.type, text);
        } else {
            log.info("{} Channel is not active!", this.type);
        }
    }

    @Override
    public void sendBinary(ByteBuf buf) {
        if (channel != null && channel.isActive()) {
            WebSocketFrame frame = new BinaryWebSocketFrame(buf);
            channel.writeAndFlush(frame);
        } else {
            log.info("{} Channel is not active!", this.type);
        }
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
        }
    }
}
