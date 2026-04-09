package com.mrcp.proxy.handler;


import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.handler.status.TtsEvent;
import com.mrcp.proxy.handler.status.TtsStateMachine;
import com.mrcp.proxy.utils.MrcpTTSMessage;
import com.mrcp.proxy.ws.TtsConfig;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public abstract class AbstractTTSHandler implements TTSHandler, ClientCallBack {

    protected final Channel serverChannel;
    protected final String url;
    protected JSONObject event;
    protected WebSocketClient client;
    protected final TtsStateMachine stateMachine = new TtsStateMachine();

    private final boolean audioSaveEnabled;
    private final String audioSaveDir;
    private FileOutputStream audioOutputStream;
    private String audioFilePath;

    public AbstractTTSHandler(Channel serverChannel, TtsConfig config) {
        this.serverChannel = serverChannel;
        this.url = config.getTtsUrl();
        this.audioSaveEnabled = config.isTtsAudioSaveEnabled();
        this.audioSaveDir = config.getTtsAudioSaveDir();
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        this.event = event;
        String name = event.getJSONObject("header").getString("name");

        if ("StartSynthesis".equalsIgnoreCase(name)) {
            if (!stateMachine.fire(TtsEvent.START_SYNTHESIS)) {
                return;
            }
            try {
                this.client = this.getClient();
                initAudioFile();
                client.connect();
                String text = event.getJSONObject("payload").getString("text");
                client.sendText(buildSynthesisRequest(text));
                serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildTtsStartMessage(event)));
                stateMachine.fire(TtsEvent.CLIENT_CONNECTED);
            } catch (Exception e) {
                log.error("TTS启动失败", e);
                stateMachine.fire(TtsEvent.ERROR);
                doClose();
                serverChannel.writeAndFlush(new TextWebSocketFrame(
                        MrcpTTSMessage.buildTaskFailedMessage(event, e.getMessage())));
            }
        }
    }

    @Override
    public void onClientText(Channel channel, String text) {
        if (!isSynthesisComplete(text)) {
            return;
        }
        if (!stateMachine.fire(TtsEvent.SYNTHESIS_COMPLETE)) {
            return;
        }
        doClose();
        serverChannel.writeAndFlush(new TextWebSocketFrame(MrcpTTSMessage.buildTtsCompleteMessage(event)));
        serverChannel.close();
    }

    @Override
    public void onClientBinary(Channel channel, ByteBuf msg) {
        if (!stateMachine.fire(TtsEvent.RECEIVE_AUDIO)) {
            return;
        }
        saveAudioData(msg);
        ByteBuf byteBuf = Unpooled.directBuffer(msg.capacity());
        byteBuf.writeBytes(msg);
        serverChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
    }

    private void doClose() {
        closeAudioFile();
        if (client != null) {
            client.close();
        }
    }

    protected WebSocketClient getClient() throws Exception {
        return new WebSocketClient("tts", new URI(url), this);
    }

    /**
     * 构建发给TTS服务的合成请求报文，由子类实现。
     */
    protected abstract String buildSynthesisRequest(String text);

    /**
     * 判断TTS服务返回的文本消息是否表示合成结束，由子类实现。
     */
    protected abstract boolean isSynthesisComplete(String text);

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
            String taskId = event.getJSONObject("header").getString("task_id");
            String timestamp = new SimpleDateFormat("HHmmss").format(new Date());
            String fileName = taskId + "_" + timestamp + ".pcm";
            audioFilePath = dir.getPath() + File.separator + fileName;
            audioOutputStream = new FileOutputStream(audioFilePath);
            log.info("TTS音频文件创建: {}", audioFilePath);
        } catch (IOException e) {
            log.error("创建TTS音频文件失败", e);
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
            log.error("保存TTS音频数据失败", e);
        }
    }

    private void closeAudioFile() {
        if (audioOutputStream != null) {
            try {
                audioOutputStream.flush();
                audioOutputStream.close();
                log.info("TTS音频文件保存完成: {}", audioFilePath);
            } catch (IOException e) {
                log.error("关闭TTS音频文件失败", e);
            } finally {
                audioOutputStream = null;
            }
        }
    }

}
