package com.mrcp.proxy.protocol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * SpeechSynthesizer 下行报文结构。
 */
@Data
public class SynthesisMessage {

    private MessageContext context;
    private MessageHeader header;
    private Payload payload;

    public static SynthesisMessage parse(String json) {
        return JSON.parseObject(json, SynthesisMessage.class);
    }


    @Data
    public static class Payload {

        private String format;
        private Integer method;

        @JSONField(name = "pitch_rate")
        private Integer pitchRate;

        @JSONField(name = "sample_rate")
        private Integer sampleRate;

        @JSONField(name = "speech_rate")
        private Integer speechRate;

        private String text;
        private String voice;
        private Integer volume;
    }

    public static void main(String[] args) {
        String json = " {\n" +
                "    \"context\": {\n" +
                "        \"app\": {\n" +
                "            \"developer\": \"beiyu\",\n" +
                "            \"name\": \"sdm\",\n" +
                "            \"version\": \"c7b489cc81f069b51c92c4b1f6fe403c56030851\"\n" +
                "        },\n" +
                "        \"sdk\": {\n" +
                "            \"language\": \"C++\",\n" +
                "            \"name\": \"nls-sdk-linux\",\n" +
                "            \"version\": \"2.3.20\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"header\": {\n" +
                "        \"appkey\": \"1111\",\n" +
                "        \"message_id\": \"080434376305483c9e6a1c8bf67c41e9\",\n" +
                "        \"name\": \"StartSynthesis\",\n" +
                "        \"namespace\": \"SpeechSynthesizer\",\n" +
                "        \"task_id\": \"aa0796fdfbef486aa7d9b8cb5f46a305\"\n" +
                "    },\n" +
                "    \"payload\": {\n" +
                "        \"format\": \"pcm\",\n" +
                "        \"method\": 0,\n" +
                "        \"pitch_rate\": 0,\n" +
                "        \"sample_rate\": 8000,\n" +
                "        \"speech_rate\": 0,\n" +
                "        \"text\": \"欢迎使用。\",\n" +
                "        \"voice\": \"aixia\",\n" +
                "        \"volume\": 50\n" +
                "    }\n" +
                "}";
        System.out.println(parse(json));
    }

}
