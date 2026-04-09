package com.mrcp.proxy.handler;


import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
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

    private final boolean audioSaveEnabled;
    private final String audioSaveDir;
    private FileOutputStream audioOutputStream;
    private String audioFilePath;

    public AbstractTTSHandler(Channel serverChannel, String url, boolean audioSaveEnabled, String audioSaveDir) {
        this.serverChannel = serverChannel;
        this.url = url;
        this.audioSaveEnabled = audioSaveEnabled;
        this.audioSaveDir = audioSaveDir;
    }

    @Override
    public void onServerText(JSONObject event) throws Exception {
        this.event = event;
        this.client = this.getClient();
        initAudioFile();
    }

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

    protected WebSocketClient getClient() throws Exception {
        return new WebSocketClient("tts", new URI(url), this);
    }

    public abstract void onClientText(Channel channel, String text);

    @Override
    public void onClientBinary(Channel channel, ByteBuf msg){
//        log.info("tts音频数据 -> 转发给 mrcp server");
        saveAudioData(msg);

        ByteBuf byteBuf = Unpooled.directBuffer(msg.capacity());
        byteBuf.writeBytes(msg);
        serverChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
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

    protected void closeAudioFile() {
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
