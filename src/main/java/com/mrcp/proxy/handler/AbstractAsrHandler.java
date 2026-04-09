package com.mrcp.proxy.handler;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public abstract class AbstractAsrHandler implements AsrHandler, ClientCallBack {

    protected final Channel serverChannel;
    protected final String url;
    protected JSONObject event;
    protected WebSocketClient client;
    
    private final boolean audioSaveEnabled;
    private final String audioSaveDir;
    private FileOutputStream audioOutputStream;
    private String audioFilePath;
    
    public AbstractAsrHandler(Channel serverChannel, String url, boolean audioSaveEnabled, String audioSaveDir) {
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

    @Override
    public void onServerBinary(ByteBuf buf) {
//        log.info("mrcp server音频数据包 -> 转发给asr服务器");
        saveAudioData(buf);
        
        ByteBuf byteBuf = Unpooled.directBuffer(buf.capacity());
        byteBuf.writeBytes(buf);

        client.sendBinary(byteBuf);
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

    protected WebSocketClient getClient() throws Exception {
        return new WebSocketClient("asr", new URI(url), this);
    }

    @Override
    public void closeClient() {
        closeAudioFile();
        if (client != null) {
            client.close();
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

    public abstract void onClientText(Channel channel, String text);

    public void onClientBinary(Channel channel, ByteBuf buf) {

    }

}
