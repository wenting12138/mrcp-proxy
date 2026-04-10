package com.mrcp.proxy.ws.client.ali;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.mrcp.proxy.ws.client.ClientCallBack;
import com.mrcp.proxy.ws.client.WebSocketClient;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云实时语音识别 SDK（{@link SpeechTranscriber}）封装，对齐 {@link WebSocketClient} 生命周期。
 * <p>
 * 通过 {@link #connect(String)} 建立连接并启动识别，{@link #sendBinary(ByteBuf)} 发送音频流，
 * 识别结果通过 {@link ClientCallBack#onClientText(String)} 回调。
 * <p>
 * 回调文本格式：
 * <ul>
 *   <li>中间结果 — JSON: {@code {"result":"...","isFinal":false}}</li>
 *   <li>一句话结束 — JSON: {@code {"result":"...","isFinal":true}}</li>
 *   <li>识别完成 — {@code "EOS"}</li>
 *   <li>识别失败 — {@code "ALI_FAIL:..."}</li>
 * </ul>
 */
@Slf4j
public class AliAsrClient implements WebSocketClient {

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final ClientCallBack clientCallBack;

    private final Object lock = new Object();
    private NlsClient nlsClient;
    private SpeechTranscriber transcriber;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public AliAsrClient(String appKey, String accessKeyId, String accessKeySecret, ClientCallBack clientCallBack) {
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
            if (url == null || url.isEmpty()) {
                nlsClient = new NlsClient(token);
            } else {
                nlsClient = new NlsClient(url, token);
            }

            transcriber = new SpeechTranscriber(nlsClient, buildListener());
            transcriber.setAppKey(appKey);
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
            transcriber.setEnableIntermediateResult(true);
            transcriber.setEnablePunctuation(true);
            transcriber.setEnableITN(false);

            long t0 = System.currentTimeMillis();
            transcriber.start();
            started.set(true);
            log.info("Ali ASR start latency: {} ms", System.currentTimeMillis() - t0);
        }
    }

    @Override
    public void sendText(String text) {
        log.debug("AliAsrClient sendText ignored (Ali ASR is binary-driven)");
    }

    @Override
    public void sendBinary(ByteBuf buf) {
        if (!started.get()) {
            log.warn("AliAsrClient sendBinary ignored: transcriber not started");
            return;
        }
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            transcriber.send(bytes);
        } catch (Exception e) {
            log.error("Ali ASR send audio error", e);
            notifyFail(e.getMessage());
        } finally {
            buf.release();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (transcriber != null && started.compareAndSet(true, false)) {
                try {
                    long t0 = System.currentTimeMillis();
                    transcriber.stop();
                    log.info("Ali ASR stop latency: {} ms", System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    log.warn("Ali ASR transcriber stop error", e);
                } finally {
                    transcriber.close();
                    transcriber = null;
                }
            }
            if (nlsClient != null) {
                try {
                    nlsClient.shutdown();
                } catch (Exception e) {
                    log.warn("Ali NlsClient shutdown error", e);
                }
                nlsClient = null;
            }
        }
    }

    private SpeechTranscriberListener buildListener() {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                log.info("Ali ASR started, task_id: {}, status: {}", response.getTaskId(), response.getStatus());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                log.debug("Ali ASR sentence begin, task_id: {}, index: {}",
                        response.getTaskId(), response.getTransSentenceIndex());
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                log.debug("Ali ASR intermediate, task_id: {}, index: {}, result: {}",
                        response.getTaskId(), response.getTransSentenceIndex(), response.getTransSentenceText());
                if (clientCallBack != null) {
                    String result = buildResultJson(response.getTransSentenceText(), false);
                    clientCallBack.onClientText(result);
                }
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                log.info("Ali ASR sentence end, task_id: {}, index: {}, result: {}, confidence: {}",
                        response.getTaskId(), response.getTransSentenceIndex(),
                        response.getTransSentenceText(), response.getConfidence());
                if (clientCallBack != null) {
                    String result = buildResultJson(response.getTransSentenceText(), true);
                    clientCallBack.onClientText(result);
                }
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                log.info("Ali ASR complete, task_id: {}, status: {}", response.getTaskId(), response.getStatus());
                if (clientCallBack != null) {
                    clientCallBack.onClientText("EOS");
                }
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                log.error("Ali ASR failed, task_id: {}, status: {}, status_text: {}",
                        response.getTaskId(), response.getStatus(), response.getStatusText());
                notifyFail(response.getStatusText());
            }
        };
    }

    private static String buildResultJson(String text, boolean isFinal) {
        return "{\"result\":\"" + (text == null ? "" : text.replace("\"", "\\\""))
                + "\",\"isFinal\":" + isFinal + "}";
    }

    private void notifyFail(String message) {
        if (clientCallBack != null) {
            clientCallBack.onClientText("ALI_FAIL:" + (message == null ? "unknown" : message));
        }
    }
}
