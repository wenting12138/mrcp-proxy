package com.mrcp.proxy.utils;

import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSONObject;
import com.mrcp.proxy.protocol.SynthesisMessage;
import com.mrcp.proxy.protocol.TranscriptionMessage;

public class MrcpTTSMessage {
    /**
     * @param fromEvent
     * @return
     */
    public static String buildTtsStartMessage(SynthesisMessage fromEvent){
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "SynthesisStarted");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 20000000);
        header.put("status_text", "GATEWAY|SUCCESS|Success.");
        message.put("header", header);
        return message.toJSONString();
    }

    public static String buildTtsCompleteMessage(SynthesisMessage fromEvent){
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "SynthesisCompleted");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 20000000);
        header.put("status_text", "GATEWAY|SUCCESS|Success.");
        message.put("header", header);
        return message.toJSONString();
    }

    /**
     *  {
     *     "header": {
     *         "namespace": "SpeechTranscriber",
     *         "name": "TranscriptionStarted",
     *         "status": 20000000,
     *         "message_id": "083b65a5e91949c39f285e80de697dda",
     *         "task_id": "40cba489da054414a0539eb2a0eca902",
     *         "status_text": "Gateway:SUCCESS:Success."
     *     }
     * }
     * @param fromEvent
     * @return
     */
    public static String buildAsrStartMessage(TranscriptionMessage fromEvent){
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "TranscriptionStarted");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 20000000);
        header.put("status_text", "GATEWAY|SUCCESS|Success.");
        message.put("header", header);
        return message.toJSONString();
    }

    /**
     *  {
     *     "header": {
     *         "namespace": "SpeechTranscriber",
     *         "name": "TranscriptionResultChanged",
     *         "status": 20000000,
     *         "message_id": "08afcb366e2a4c489a70dd1452167a70",
     *         "task_id": "7d57b640ecaa4187b87a609f0b4adfd3",
     *         "status_text": "Gateway:SUCCESS:Success."
     *     },
     *     "payload": {
     *         "index": 1,
     *         "time": 1680,
     *         "result": "转",
     *         "confidence": 0.864,
     *         "words": [
     *
     *         ],
     *         "status": 0,
     *         "fixed_result": "",
     *         "unfixed_result": ""
     *     }
     * }
     * @param fromEvent
     * @param text
     * @return
     */
    public static String buildAsrResultChangedMessage(TranscriptionMessage fromEvent, String text){
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "TranscriptionResultChanged");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 20000000);
        header.put("status_text", "GATEWAY|SUCCESS|Success.");
        message.put("header", header);
        JSONObject payload = new JSONObject();
        payload.put("index", 1);
        payload.put("result", text);
        payload.put("confidence", 0.99);
        message.put("payload", payload);
        return message.toJSONString();
    }

    /**
     * {
     *     "header": {
     *         "namespace": "SpeechTranscriber",
     *         "name": "SentenceEnd",
     *         "status": 20000000,
     *         "message_id": "a68f28f3920e40ce81b64890e6f54fd4",
     *         "task_id": "7d57b640ecaa4187b87a609f0b4adfd3",
     *         "status_text": "Gateway:SUCCESS:Success."
     *     },
     *     "payload": {
     *         "index": 1,
     *         "time": 2360,
     *         "result": "转人工。",
     *         "confidence": 0.885,
     *         "words": [
     *
     *         ],
     *         "status": 0,
     *         "gender": "",
     *         "begin_time": 1180,
     *         "fixed_result": "",
     *         "unfixed_result": "",
     *         "stash_result": {
     *             "sentenceId": 2,
     *             "beginTime": 2360,
     *             "text": "",
     *             "fixedText": "",
     *             "unfixedText": "",
     *             "currentTime": 2360,
     *             "words": [
     *
     *             ]
     *         },
     *         "audio_extra_info": "",
     *         "sentence_id": "b9388166299d4190a08b6e9e077b239d",
     *         "gender_score": 0.0,
     *         "emo_tag": "neutral",
     *         "emo_confidence": 0.965
     *     }
     * }
     * @param fromEvent
     * @param text
     * @return
     */
    public static String buildAsrResultMessage(TranscriptionMessage fromEvent, String text) {
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "SentenceEnd");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 20000000);
        header.put("status_text", "GATEWAY|SUCCESS|Success.");
        message.put("header", header);
        JSONObject payload = new JSONObject();
        payload.put("index", 1);
        payload.put("result", text);
        payload.put("confidence", 0.99);
        payload.put("status", 0);
        payload.put("gender", "");
        message.put("payload", payload);
        return message.toJSONString();
    }

    public static String buildTtsTaskFailedMessage(SynthesisMessage fromEvent, String reason) {
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "TaskFailed");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 40000000);
        header.put("status_text", "GATEWAY|ERROR|" + reason);
        message.put("header", header);
        return message.toJSONString();
    }

    public static String buildAsrTaskFailedMessage(TranscriptionMessage fromEvent, String reason) {
        JSONObject message = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("namespace", fromEvent.getHeader().getNamespace());
        header.put("name", "TaskFailed");
        header.put("task_id", fromEvent.getHeader().getTaskId());
        header.put("message_id", UUID.fastUUID().toString());
        header.put("status", 40000000);
        header.put("status_text", "GATEWAY|ERROR|" + reason);
        message.put("header", header);
        return message.toJSONString();
    }
}
