package com.mrcp.proxy.ws.client.ali;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云语音合成 SDK（{@link SpeechSynthesizer}）封装，对齐 {@link WebSocketClient} 生命周期。
 * <p>
 * {@link #sendText(String)} 接收 JSON：至少含 {@code text}；可选 {@code voice}/{@code voiceName}、
 * {@code sampleRate}、{@code format}（wav/pcm/mp3）、{@code pitchRate}、{@code speechRate}。
 */
@Slf4j
public class AliTtsClient implements WebSocketClient {

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final ClientCallBack clientCallBack;

    private final Object lock = new Object();
    private final ExecutorService synthExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ali-tts-synth");
        t.setDaemon(true);
        return t;
    });

    private NlsClient nlsClient;

    public AliTtsClient(String appKey, String accessKeyId, String accessKeySecret, ClientCallBack clientCallBack) {
        this.appKey = appKey;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.clientCallBack = clientCallBack;
    }

    @Override
    public void connect(String url) throws Exception {
        synchronized (lock) {
            if (nlsClient != null) {
                return;
            }
            AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
            accessToken.apply();
            log.debug("Ali NLS token acquired, expire time: {}", accessToken.getExpireTime());
            String token = accessToken.getToken();
            if (url == null) {
                nlsClient = new NlsClient(token);
            } else {
                nlsClient = new NlsClient(url, token);
            }
        }
    }

    @Override
    public void sendText(String text) {
        NlsClient client;
        synchronized (lock) {
            client = nlsClient;
        }
        if (client == null) {
            log.warn("AliClient sendText ignored: not connected");
            return;
        }
        synthExecutor.execute(() -> runSynthesis(client, text));
    }

    private void runSynthesis(NlsClient client, String payload) {
        SpeechSynthesizer synthesizer = null;
        try {
            JSONObject json;
            try {
                json = JSONObject.parseObject(payload);
            } catch (Exception e) {
                json = new JSONObject();
                json.put("text", payload);
            }
            String speechText = json.getString("text");
            if (speechText == null || speechText.isEmpty()) {
                log.warn("Ali TTS missing text in JSON: {}", payload);
                notifyFail("missing text in request json");
                return;
            }

            AtomicBoolean firstBinary = new AtomicBoolean(true);
            long[] startAfterStart = new long[1];

            SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    log.info("Ali TTS complete, task_id: {}, status: {}", response.getTaskId(), response.getStatus());
                    if (clientCallBack != null) {
                        clientCallBack.onClientText("EOS");
                    }
                }

                @Override
                public void onMessage(ByteBuffer message) {
                    if (firstBinary.compareAndSet(true, false)) {
                        log.info("Ali TTS first binary latency: {} ms",
                                System.currentTimeMillis() - startAfterStart[0]);
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes);
                    if (clientCallBack != null) {
                        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                        clientCallBack.onClientBinary(buf);
                    }
                }

                @Override
                public void onFail(SpeechSynthesizerResponse response) {
                    log.error("Ali TTS failed, task_id: {}, status: {}, status_text: {}",
                            response.getTaskId(), response.getStatus(), response.getStatusText());
                    notifyFail(response.getStatusText());
                }
            };

            synthesizer = new SpeechSynthesizer(client, listener);
            synthesizer.setAppKey(appKey);
            synthesizer.setFormat(parseFormat(json.getString("format")));
            synthesizer.setSampleRate(parseSampleRate(json.getInteger("sampleRate")));
            synthesizer.setVoice(json.getString("voice") != null ? json.getString("voice")
                    : json.getString("voiceName") != null ? json.getString("voiceName") : "siyue");

            Integer pitch = json.getInteger("pitchRate");
            if (pitch != null) {
                synthesizer.setPitchRate(pitch);
            } else {
                synthesizer.setPitchRate(100);
            }
            Integer rate = json.getInteger("speechRate");
            if (rate != null) {
                synthesizer.setSpeechRate(rate);
            } else {
                synthesizer.setSpeechRate(100);
            }

            Boolean subtitle = json.getBoolean("enable_subtitle");
            synthesizer.addCustomedParam("enable_subtitle", subtitle != null ? subtitle : false);

            synthesizer.setText(speechText);
            long t0 = System.currentTimeMillis();
            synthesizer.start();
            log.info("Ali TTS start latency: {} ms", System.currentTimeMillis() - t0);
            startAfterStart[0] = System.currentTimeMillis();
            synthesizer.waitForComplete();
            log.info("Ali TTS waitForComplete finished");
        } catch (Exception e) {
            log.error("Ali TTS synthesis error", e);
            notifyFail(e.getMessage());
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }

    private void notifyFail(String message) {
        if (clientCallBack != null) {
            clientCallBack.onClientText("ALI_FAIL:" + (message == null ? "unknown" : message));
        }
    }

    private static OutputFormatEnum parseFormat(String format) {
        if (format == null || format.isEmpty()) {
            return OutputFormatEnum.WAV;
        }
        switch (format.trim().toLowerCase()) {
            case "pcm":
                return OutputFormatEnum.PCM;
            case "mp3":
                return OutputFormatEnum.MP3;
            default:
                return OutputFormatEnum.WAV;
        }
    }

    private static SampleRateEnum parseSampleRate(Integer hz) {
        if (hz == null) {
            return SampleRateEnum.SAMPLE_RATE_16K;
        }
        if (hz <= 8000) {
            return SampleRateEnum.SAMPLE_RATE_8K;
        }
        return SampleRateEnum.SAMPLE_RATE_16K;
    }

    @Override
    public void sendBinary(ByteBuf buf) {
        log.debug("AliClient sendBinary ignored (Ali TTS is text-driven)");
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (nlsClient != null) {
                try {
                    nlsClient.shutdown();
                } catch (Exception e) {
                    log.warn("Ali NlsClient shutdown", e);
                }
                nlsClient = null;
            }
        }
        synthExecutor.shutdown();
    }
}
