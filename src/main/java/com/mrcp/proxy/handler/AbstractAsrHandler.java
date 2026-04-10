package com.mrcp.proxy.handler;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.status.AsrEvent;
import com.mrcp.proxy.handler.status.AsrState;
import com.mrcp.proxy.handler.status.AsrStateMachine;
import com.mrcp.proxy.utils.MrcpTTSMessage;
import com.mrcp.proxy.ws.AsrConfig;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.netty.NettyWebSocketClient;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public abstract class AbstractAsrHandler implements AsrHandler, ClientCallBack {

    protected final Channel serverChannel;
    protected final String url;
    protected JSONObject event;
    protected WebSocketClient client;
    protected final AsrStateMachine stateMachine = new AsrStateMachine();

    private final boolean audioSaveEnabled;
    private final String audioSaveDir;
    private FileOutputStream audioOutputStream;
    private String audioFilePath;

    public AbstractAsrHandler(Channel serverChannel, AsrConfig config) {
        this.serverChannel = serverChannel;
        this.url = config.getAsrUrl();
        this.audioSaveEnabled = config.isAsrAudioSaveEnabled();
        this.audioSaveDir = config.getAsrAudioSaveDir();
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        this.event = event;
        String name = event.getJSONObject("header").getString("name");

        if ("StartTranscription".equalsIgnoreCase(name)) {
            if (!stateMachine.fire(AsrEvent.START_TRANSCRIPTION)) {
                return;
            }
            try {
                this.client = this.getClient();
                initAudioFile();
                client.connect(url);
                String sessionId = event.getJSONObject("context").getString("session_id");
                client.sendText(buildConnectRequest(sessionId));
                serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildAsrStartMessage(event)));
                stateMachine.fire(AsrEvent.CLIENT_CONNECTED);
            } catch (Exception e) {
                log.error("ASR启动失败", e);
                stateMachine.fire(AsrEvent.ERROR);
                doClose();
                serverChannel.writeAndFlush(new TextWebSocketFrame(
                        MrcpTTSMessage.buildTaskFailedMessage(event, e.getMessage())));
            }
        } else if ("StopTranscription".equalsIgnoreCase(name)) {
            if (!stateMachine.fire(AsrEvent.STOP_TRANSCRIPTION)) {
                return;
            }
            doClose();
        }
    }

    @Override
    public void onServerBinary(ByteBuf buf) {
        if (!stateMachine.fire(AsrEvent.RECEIVE_AUDIO)) {
            return;
        }
        saveAudioData(buf);
        ByteBuf byteBuf = Unpooled.directBuffer(buf.capacity());
        byteBuf.writeBytes(buf);
        client.sendBinary(byteBuf);
    }

    @Override
    public void onClientText(String text) {
        if (!stateMachine.fire(AsrEvent.CLIENT_RESULT)) {
            return;
        }
        AsrResult result = parseResult(text);
        if (result == null) {
            return;
        }
        if (result.isFinal) {
            log.info("ASR识别结果: {}", result.text);
            serverChannel.writeAndFlush(new TextWebSocketFrame(
                    MrcpTTSMessage.buildAsrResultMessage(event, result.text)));
        } else {
            serverChannel.writeAndFlush(new TextWebSocketFrame(
                    MrcpTTSMessage.buildAsrResultChangedMessage(event, result.text)));
        }
    }

    @Override
    public void onClientBinary(ByteBuf buf) {
    }

    @Override
    public void closeClient() {
        if (!stateMachine.fire(AsrEvent.DISCONNECT)) {
            return;
        }
        doClose();
    }

    private void doClose() {
        closeAudioFile();
        if (client != null) {
            client.close();
        }
    }

    protected WebSocketClient getClient() throws Exception {
        return new NettyWebSocketClient("asr", this);
    }

    public AsrState getState() {
        return stateMachine.getCurrentState();
    }

    /**
     * 构建连接ASR服务后发送的首条请求报文，由子类实现。
     */
    protected abstract String buildConnectRequest(String sessionId);

    /**
     * 解析ASR服务返回的识别结果，由子类实现。
     * 返回 null 表示该消息不需要处理。
     */
    protected abstract AsrResult parseResult(String text);

    protected static class AsrResult {
        public final String text;
        public final boolean isFinal;

        public AsrResult(String text, boolean isFinal) {
            this.text = text;
            this.isFinal = isFinal;
        }
    }

    // ---- 音频保存 ----

    private void initAudioFile() {
        if (!audioSaveEnabled) {
            return;
        }
        try {
            String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
            File dir = new File(audioSaveDir + File.separator + dateDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String sessionId = event.getJSONObject("context").getString("session_id");
            String timestamp = new SimpleDateFormat("HHmmss").format(new Date());
            String fileName = sessionId + "_" + timestamp + ".pcm";
            audioFilePath = dir.getPath() + File.separator + fileName;
            audioOutputStream = new FileOutputStream(audioFilePath);
            log.info("ASR音频文件创建: {}", audioFilePath);
        } catch (IOException e) {
            log.error("创建音频文件失败", e);
        }
    }

    private void saveAudioData(ByteBuf buf) {
        if (audioOutputStream == null) {
            return;
        }
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            audioOutputStream.write(bytes);
        } catch (IOException e) {
            log.error("保存音频数据失败", e);
        }
    }

    private void closeAudioFile() {
        if (audioOutputStream != null) {
            try {
                audioOutputStream.flush();
                audioOutputStream.close();
                log.info("ASR音频文件保存完成: {}", audioFilePath);
            } catch (IOException e) {
                log.error("关闭音频文件失败", e);
            } finally {
                audioOutputStream = null;
            }
        }
    }

}
