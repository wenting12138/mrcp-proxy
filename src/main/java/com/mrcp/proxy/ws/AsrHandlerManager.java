package com.mrcp.proxy.ws;

import com.mrcp.proxy.handler.AsrHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AsrHandlerManager {

    private static final Map<String, AsrHandler> asrHandlerMap = new ConcurrentHashMap<>();

    public static void put(String sessionId, AsrHandler asrHandler) {
        asrHandlerMap.put(sessionId, asrHandler);
    }

    public static AsrHandler get(String sessionId) {
        return asrHandlerMap.get(sessionId);
    }

    public static void remove(String sessionId) {
        asrHandlerMap.remove(sessionId);
    }
}
