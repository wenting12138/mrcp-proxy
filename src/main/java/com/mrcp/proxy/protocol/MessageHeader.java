package com.mrcp.proxy.protocol;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class MessageHeader {
    private String appkey;
    @JSONField(name = "message_id")
    private String messageId;
    private String name;
    private String namespace;
    @JSONField(name = "task_id")
    private String taskId;
}
