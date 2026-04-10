package com.mrcp.proxy.handler;

import com.mrcp.proxy.protocol.TranscriptionMessage;
import io.netty.buffer.ByteBuf;

public interface AsrHandler {

    void onServerText(TranscriptionMessage event) throws Exception;

    void onServerBinary(ByteBuf buf);

    void closeClient();

}
