package com.mrcp.proxy.handler.status;

import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class TtsStateMachine {

    private volatile TtsState currentState = TtsState.IDLE;

    private final Map<TtsState, Map<TtsEvent, TtsState>> transitionTable = new EnumMap<>(TtsState.class);

    public TtsStateMachine() {
        initTransitions();
    }

    private void initTransitions() {
        addTransition(TtsState.IDLE, TtsEvent.START_SYNTHESIS, TtsState.CONNECTING);

        addTransition(TtsState.CONNECTING, TtsEvent.CLIENT_CONNECTED, TtsState.SYNTHESIZING);
        addTransition(TtsState.CONNECTING, TtsEvent.ERROR, TtsState.CLOSED);
        addTransition(TtsState.CONNECTING, TtsEvent.DISCONNECT, TtsState.CLOSED);

        addTransition(TtsState.SYNTHESIZING, TtsEvent.RECEIVE_AUDIO, TtsState.SYNTHESIZING);
        addTransition(TtsState.SYNTHESIZING, TtsEvent.SYNTHESIS_COMPLETE, TtsState.COMPLETED);
        addTransition(TtsState.SYNTHESIZING, TtsEvent.DISCONNECT, TtsState.CLOSED);
        addTransition(TtsState.SYNTHESIZING, TtsEvent.ERROR, TtsState.CLOSED);

        addTransition(TtsState.COMPLETED, TtsEvent.DISCONNECT, TtsState.CLOSED);
    }

    private void addTransition(TtsState from, TtsEvent event, TtsState to) {
        transitionTable
                .computeIfAbsent(from, k -> new EnumMap<>(TtsEvent.class))
                .put(event, to);
    }

    public synchronized boolean fire(TtsEvent event) {
        Map<TtsEvent, TtsState> events = transitionTable.get(currentState);
        if (events == null) {
            log.warn("TTS状态机: 状态 {} 无任何转换规则, 忽略事件 {}", currentState, event);
            return false;
        }
        TtsState target = events.get(event);
        if (target == null) {
            log.warn("TTS状态机: 状态 {} 不接受事件 {}, 忽略", currentState, event);
            return false;
        }
        TtsState fromState = currentState;
        currentState = target;
        if (fromState != currentState) {
            log.info("TTS状态机: {} --[{}]--> {}", fromState, event, currentState);
        }
        return true;
    }

    public synchronized TtsState getCurrentState() {
        return currentState;
    }

    public synchronized boolean isState(TtsState state) {
        return currentState == state;
    }

}
