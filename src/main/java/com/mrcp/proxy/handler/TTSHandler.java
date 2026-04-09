package com.mrcp.proxy.handler;

import com.alibaba.fastjson.JSONObject;

public interface TTSHandler {

    void onServerText(JSONObject event) throws Exception;

}
