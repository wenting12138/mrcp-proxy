package com.mrcp.proxy.handler;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public interface AsrHandler {

    void onServerText(JSONObject event) throws Exception;

    void onServerBinary(ByteBuf buf);

    void closeClient();

}
