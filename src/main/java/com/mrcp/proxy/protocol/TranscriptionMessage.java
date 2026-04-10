package com.mrcp.proxy.protocol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * SpeechTranscriber / StartTranscription 下行报文结构。
 */
@Data
public class TranscriptionMessage {

    private MessageContext context;
    private MessageHeader header;
    private Payload payload;

    public static TranscriptionMessage parse(String json) {
        return JSON.parseObject(json, TranscriptionMessage.class);
    }

    @Data
    public static class Payload {

        @JSONField(name = "enable_ignore_sentence_timeout")
        private Boolean enableIgnoreSentenceTimeout;

        @JSONField(name = "enable_intermediate_result")
        private Boolean enableIntermediateResult;

        @JSONField(name = "enable_inverse_text_normalization")
        private Boolean enableInverseTextNormalization;

        @JSONField(name = "enable_punctuation_prediction")
        private Boolean enablePunctuationPrediction;

        @JSONField(name = "enable_semantic_sentence_detection")
        private Boolean enableSemanticSentenceDetection;

        private String format;

        @JSONField(name = "max_sentence_silence")
        private Integer maxSentenceSilence;

        @JSONField(name = "sample_rate")
        private Integer sampleRate;
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
                "        },\n" +
                "        \"session_id\": \"37ead8eace174a5a\"\n" +
                "    },\n" +
                "    \"header\": {\n" +
                "        \"appkey\": \"111111\",\n" +
                "        \"message_id\": \"532a3964e6cc4a968867e41a290a0b91\",\n" +
                "        \"name\": \"StartTranscription\",\n" +
                "        \"namespace\": \"SpeechTranscriber\",\n" +
                "        \"task_id\": \"9dc290a7410449c882185544bfece96b\"\n" +
                "    },\n" +
                "    \"payload\": {\n" +
                "        \"enable_ignore_sentence_timeout\": true,\n" +
                "        \"enable_intermediate_result\": true,\n" +
                "        \"enable_inverse_text_normalization\": true,\n" +
                "        \"enable_punctuation_prediction\": true,\n" +
                "        \"enable_semantic_sentence_detection\": false,\n" +
                "        \"format\": \"pcm\",\n" +
                "        \"max_sentence_silence\": 800,\n" +
                "        \"sample_rate\": 8000\n" +
                "    }\n" +
                "}";

        System.out.println(TranscriptionMessage.parse(json));
    }
}
