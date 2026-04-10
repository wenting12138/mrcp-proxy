package com.mrcp.proxy.protocol;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class MessageContext {

    @JSONField(name = "app")
    private AppInfo app;

    private SdkInfo sdk;

    @JSONField(name = "session_id")
    private String sessionId;

    @Data
    public static class AppInfo {

        private String developer;
        private String name;
        private String version;
    }

    @Data
    public static class SdkInfo {

        private String language;
        private String name;
        private String version;
    }

}
