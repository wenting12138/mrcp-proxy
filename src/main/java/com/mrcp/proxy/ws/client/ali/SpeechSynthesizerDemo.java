package com.mrcp.proxy.ws.client.ali;

import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.ws.client.ClientCallBack;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 语音合成示例：通过 {@link AliTtsClient} 调用阿里云流式 TTS（原 SDK 示例逻辑已迁入 {@link AliTtsClient}）。
 */
public class SpeechSynthesizerDemo {

    private static final Logger logger = LoggerFactory.getLogger(SpeechSynthesizerDemo.class);

    private static final class FileSavingCallback implements ClientCallBack, AutoCloseable {

        private final FileOutputStream fout;
        private final CountDownLatch done;
        private final AtomicBoolean terminal = new AtomicBoolean(false);

        FileSavingCallback(File out, CountDownLatch done) throws IOException {
            this.fout = new FileOutputStream(out);
            this.done = done;
        }

        @Override
        public void onClientText(String text) {
            if (text != null && text.startsWith("ALI_FAIL:")) {
                logger.error("TTS failed: {}", text);
                finish();
                return;
            }
            if ("EOS".equals(text)) {
                logger.info("TTS complete");
                finish();
            }
        }

        @Override
        public void onClientBinary(ByteBuf msg) {
            try {
                byte[] bytes = new byte[msg.readableBytes()];
                msg.getBytes(msg.readerIndex(), bytes);
                fout.write(bytes);
            } catch (IOException e) {
                logger.error("write audio", e);
            }
        }

        private void finish() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                fout.flush();
                fout.close();
            } catch (IOException e) {
                logger.error("close output", e);
            } finally {
                done.countDown();
            }
        }

        @Override
        public void close() {
            finish();
        }
    }

    public static void main(String[] args) throws Exception {
        String appKey = System.getenv("NLS_APP_KEY");
        String id = System.getenv("ALIYUN_AK_ID");
        String secret = System.getenv("ALIYUN_AK_SECRET");
        String url = System.getenv().getOrDefault("NLS_GATEWAY_URL", "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1");

        File out = new File("tts_test.wav");
        CountDownLatch done = new CountDownLatch(1);

        try (FileSavingCallback callback = new FileSavingCallback(out, done)) {
            AliTtsClient client = new AliTtsClient(appKey, id, secret, callback);
            try {
                client.connect(url);

                JSONObject req = new JSONObject();
                req.put("text", "欢迎使用阿里巴巴智能语音合成服务，您可以说北京明天天气怎么样啊");
                req.put("format", "wav");
                req.put("sampleRate", 16000);
                req.put("voice", "siyue");
                req.put("pitchRate", 100);
                req.put("speechRate", 100);
                req.put("enable_subtitle", false);

                client.sendText(req.toJSONString());

                if (!done.await(5, TimeUnit.MINUTES)) {
                    logger.error("TTS timed out waiting for completion");
                }
                logger.info("output file: {}", out.getAbsolutePath());
            } finally {
                client.close();
            }
        }
    }
}
