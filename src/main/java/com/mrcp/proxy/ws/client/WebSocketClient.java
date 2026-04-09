package com.mrcp.proxy.ws.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class WebSocketClient {

    private final String name;
    private final URI uri;
    private final SslContext sslCtx;
    private Channel channel;
    private final WebSocketClientHandshaker handshaker;
    private final WebSocketClientHandler handler;
    private final ClientCallBack clientCallBack;

    public WebSocketClient(String name, URI uri, ClientCallBack clientCallBack) throws Exception {
        this.name = name;
        this.uri = uri;
        this.clientCallBack = clientCallBack;

        // 如果是 wss（WebSocket Secure），需要启用 SSL
        boolean useSsl = "wss".equalsIgnoreCase(uri.getScheme());
        sslCtx = useSsl ? SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build() : null;

        // 创建握手器
        handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri,
                WebSocketVersion.V13,
                null,
                true,
                new DefaultHttpHeaders(),
                10 *  1024 * 1024
        );

        // 创建处理器
        handler = new WebSocketClientHandler(name, handshaker, this.clientCallBack);
    }

    public void connect() throws Exception {
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

            log.info("{} Connecting to {}:{}", this.name, uri.getHost(), uri.getPort());
            Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
            channel = ch;

            handler.handshakeFuture().sync();

            log.info("{} WebSocket Client connected!", this.name);
        } catch (Exception e) {
            group.shutdownGracefully();
            throw new Exception(name + " connect failed: " + uri, e);
        }
    }

    public void sendText(String text) {
        if (channel != null && channel.isActive()) {
            WebSocketFrame frame = new TextWebSocketFrame(text);
            channel.writeAndFlush(frame);
            log.info("{} Sent Client: {}", this.name, text);
        } else {
            log.info("{} Channel is not active!", this.name);
        }
    }

    public void sendBinary(ByteBuf buf) {
        if (channel != null && channel.isActive()) {
            WebSocketFrame frame = new BinaryWebSocketFrame(buf);
            channel.writeAndFlush(frame);
        } else {
            log.info("{} Channel is not active!", this.name);
        }
    }

    public void close() {
        if (channel != null) {
            channel.close();
        }
    }
}