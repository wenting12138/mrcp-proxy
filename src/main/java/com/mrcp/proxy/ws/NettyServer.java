package com.mrcp.proxy.ws;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mrcp.proxy.handler.AsrHandler;
import com.mrcp.proxy.handler.TTSHandler;
import com.mrcp.proxy.handler.asr.AsrHandlerFactory;
import com.mrcp.proxy.handler.tts.TtsHandlerFactory;
import com.mrcp.proxy.protocol.MessageContext;
import com.mrcp.proxy.protocol.MessageHeader;
import com.mrcp.proxy.protocol.SynthesisMessage;
import com.mrcp.proxy.protocol.TranscriptionMessage;
import com.mrcp.proxy.utils.NetworkUtil;
import com.mrcp.proxy.utils.ThreadPoolCreator;
import com.mrcp.proxy.utils.UriUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@Data
public class NettyServer {
    @Autowired
    private NettyConfig config;
    @Autowired
    private AsrHandlerFactory asrHandlerFactory;
    @Autowired
    private TtsHandlerFactory ttsHandlerFactory;
    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private static final AttributeKey<WebSocketServerHandshaker> WS_HANDSHAKER =
            AttributeKey.valueOf("ws_server_handshaker");
    private static final AttributeKey<String> ASR_SESSION_ID = AttributeKey.valueOf("asr_session_id");
    private static final AttributeKey<String> CHANNEL_TYPE = AttributeKey.valueOf("channel_type");
    private static final ThreadPoolExecutor pool = ThreadPoolCreator.create(20, "netty-biz-", 3600, 1000);
    @PostConstruct
    public void init(){
        log.info("start server ...");
        try {
            initGroup();
            this.bootstrap = new ServerBootstrap();
            this.bootstrap.group(this.bossGroup, this.workerGroup);
            this.bootstrap.channel(this.useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class);
            this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    NettyServer.this.initChannels(ch);
                }
            });
            Channel channel = bootstrap.bind(this.config.getPort()).sync().channel();
            log.info("server start success, wsPath: {}, port: {}", this.config.getWsPath(), this.config.getPort());
//            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("长链接错误: {}", e);
        }finally {
//            this.bossGroup.shutdownGracefully();
//            this.workerGroup.shutdownGracefully();
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            this.bossGroup.shutdownGracefully();
            this.workerGroup.shutdownGracefully();
        } catch (Exception e) {
            log.error("ws Server shutdown exception, ", e);
        }
    }

    private void initChannels(SocketChannel ch) {
//        ch.pipeline().addLast(new IdleStateHandler(120, 0, 0, TimeUnit.SECONDS));
        ch.pipeline().addLast("logging", new LoggingHandler("INFO"));
        ch.pipeline().addLast(new HttpServerCodec());
        ch.pipeline().addLast(new ChunkedWriteHandler());
        ch.pipeline().addLast(new HttpObjectAggregator(65536));
        ch.pipeline().addLast(new NettyConnectManageHandler());
        // 自定义的handler ，处理业务逻辑
        ch.pipeline().addLast(new NettyServerHandler());
        ch.pipeline().addLast(new WebSocketServerProtocolHandler(this.config.getWsPath(), null, true, 65536 * 10,false,true));
    }


    private void initGroup() {
        ThreadFactory bossThreadFactory = new ThreadFactoryBuilder()
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    log.error("NettyServerBoss has uncaughtException.");
                    log.error(throwable.getMessage(), throwable);
                })
                .setDaemon(true)
                .setNameFormat("netty-server-boss-%d")
                .build();
        ThreadFactory workerThreadFactory = new ThreadFactoryBuilder()
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    log.error("NettyServerWorker has uncaughtException.");
                    log.error(throwable.getMessage(), throwable);
                })
                .setDaemon(true)
                .setNameFormat("netty-server-worker-%d")
                .build();
        if (this.useEpoll()) {
            this.bossGroup = new EpollEventLoopGroup(bossThreadFactory);
            this.workerGroup = new EpollEventLoopGroup(workerThreadFactory);
        } else {
            this.bossGroup = new NioEventLoopGroup(bossThreadFactory);
            this.workerGroup = new NioEventLoopGroup(workerThreadFactory);
        }
    }

    private boolean useEpoll() {
        return NetworkUtil.isLinuxPlatform()
                && Epoll.isAvailable();
    }

    public class NettyConnectManageHandler extends ChannelDuplexHandler {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            NettyServer.this.channelConnection(ctx);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            NettyServer.this.channelClose(ctx);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NettyServer.this.channelException(ctx, cause);
        }
    }

    private void channelException(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("{} server channel error: {}", ctx.channel().attr(CHANNEL_TYPE).get(), cause.getMessage());
    }

    private void channelClose(ChannelHandlerContext ctx) throws Exception {
        log.info("{} server channel close: {}", ctx.channel().attr(CHANNEL_TYPE).get(), ctx.channel().id());
        Object sessionIdObj = ctx.channel().attr(ASR_SESSION_ID).get();
        if (sessionIdObj == null) {
            return;
        }
        String sessionId = (String) sessionIdObj;
        AsrHandler asrHandler = AsrHandlerManager.get(sessionId);
        if (asrHandler == null) {
            return;
        }
        asrHandler.closeClient();
        AsrHandlerManager.remove(sessionId);
    }

    private void channelConnection(ChannelHandlerContext ctx) throws Exception {
        log.info("server channel connect: {}", ctx.channel().id());
    }

    public class NettyServerHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof PingWebSocketFrame) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("ping"));
                return;
            }
            if (msg instanceof PongWebSocketFrame) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("pong"));
                return;
            }
            if (msg instanceof FullHttpRequest) {
                handleHttpRequest(ctx, (FullHttpRequest) msg);
                return;
            }
            if (msg instanceof TextWebSocketFrame) {
                TextWebSocketFrame text = (TextWebSocketFrame) msg;
                NettyServer.this.processReceiveMsg(ctx, text.text());
                return;
            }
            if (msg instanceof BinaryWebSocketFrame) {
                BinaryWebSocketFrame binaryWebSocketFrame = (BinaryWebSocketFrame) msg;
                NettyServer.this.processReceiveBinaryMsg(ctx, binaryWebSocketFrame.content());
                return;
            }
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        return false;
    }

    public static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        // 返回应答给客户端
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req,
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        WebSocketServerHandshaker handshaker = ctx.channel().attr(WS_HANDSHAKER).get();
        if (handshaker == null) {
            String wsUri = buildWebSocketLocation(ctx, req);
            WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsUri, null, true);
            handshaker = wsFactory.newHandshaker(req);
            ctx.channel().attr(WS_HANDSHAKER).set(handshaker);
        }
        if (handshaker == null) {
            // 不支持
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    /**
     * WebSocket 握手里的 location 需与客户端访问地址一致；优先 HTTP Host，否则用本地监听地址 + 配置端口。
     */
    private String buildWebSocketLocation(ChannelHandlerContext ctx, FullHttpRequest req) {
        String host = req.headers().get(HttpHeaderNames.HOST);
        if (host == null || host.isEmpty()) {
            int port = config.getPort() != null ? config.getPort() : 80;
            InetSocketAddress local = (InetSocketAddress) ctx.channel().localAddress();
            if (local != null && local.getPort() > 0) {
                port = local.getPort();
            }
            String ip = "127.0.0.1";
            if (local != null && local.getAddress() != null) {
                String a = local.getAddress().getHostAddress();
                if (a != null && !a.isEmpty() && !"0.0.0.0".equals(a) && !"::".equals(a)) {
                    ip = a;
                }
            }
            host = ip + ":" + port;
        }
        return "ws://" + host + req.uri();
    }

    /**
     *  收到二进制消息
     * @param ctx
     * @param content
     */
    private void processReceiveBinaryMsg(ChannelHandlerContext ctx, ByteBuf content) {
        Object sessionIdObj = ctx.channel().attr(ASR_SESSION_ID).get();
        if (sessionIdObj == null) {
            return;
        }
        String sessionId = (String) sessionIdObj;
        AsrHandler asrHandler = AsrHandlerManager.get(sessionId);
        if (asrHandler == null) {
            return;
        }
        asrHandler.onServerBinary(content);
    }

    /**
     *  收到消息
     * @param ctx
     * @param msg
     */
    private void processReceiveMsg(ChannelHandlerContext ctx, String msg) {
        MessageHeader messageHeader = JSON.parseObject(msg).getJSONObject("header").toJavaObject(MessageHeader.class);
        MessageContext messageContext = JSON.parseObject(msg).getJSONObject("context").toJavaObject(MessageContext.class);

        // SpeechSynthesizer
        String namespace = messageHeader.getNamespace();
        if ("SpeechSynthesizer".equalsIgnoreCase(namespace)) {
            pool.execute(() -> {
                try {
                    ctx.channel().attr(CHANNEL_TYPE).set(TTSHandler.HANDLER_TYPE);
                    TTSHandler ttsHandler = ttsHandlerFactory.create(ctx.channel());
                    SynthesisMessage synthesisMessage = SynthesisMessage.parse(msg);
                    ttsHandler.onServerText(synthesisMessage);
                } catch (Exception e) {
                    log.error("tts handle error: {}", e);
                }
            });
        }
        if ("SpeechTranscriber".equalsIgnoreCase(namespace)) {
            pool.execute(() -> {
                try {
                    ctx.channel().attr(CHANNEL_TYPE).set(AsrHandler.HANDLER_TYPE);
                    String name = messageHeader.getName();
                    String sessionId = messageContext.getSessionId();
                    TranscriptionMessage transcriptionMessage = TranscriptionMessage.parse(msg);
                    if ("StartTranscription".equalsIgnoreCase(name)) {
                        ctx.channel().attr(ASR_SESSION_ID).set(sessionId);
                        AsrHandler asrHandler = asrHandlerFactory.create(ctx.channel());
                        asrHandler.onServerText(transcriptionMessage);
                        AsrHandlerManager.put(sessionId, asrHandler);
                    } else {
                        AsrHandler asrHandler = AsrHandlerManager.get(sessionId);
                        if (asrHandler != null) {
                            asrHandler.onServerText(transcriptionMessage);
                        }
                    }
                } catch (Exception e) {
                    log.error("asr handle error: {}", e);
                }
            });
        }

    }

}
