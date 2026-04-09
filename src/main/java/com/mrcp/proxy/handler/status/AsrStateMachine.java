package com.mrcp.proxy.handler.status;

import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class AsrStateMachine {

    private volatile AsrState currentState = AsrState.IDLE;

    private final Map<AsrState, Map<AsrEvent, Transition>> transitionTable = new EnumMap<>(AsrState.class);

    public AsrStateMachine() {
        initTransitions();
    }

    private void initTransitions() {
        // IDLE
        addTransition(AsrState.IDLE, AsrEvent.START_TRANSCRIPTION, AsrState.CONNECTING);

        // CONNECTING
        addTransition(AsrState.CONNECTING, AsrEvent.CLIENT_CONNECTED, AsrState.RECOGNIZING);
        addTransition(AsrState.CONNECTING, AsrEvent.ERROR, AsrState.CLOSED);
        addTransition(AsrState.CONNECTING, AsrEvent.DISCONNECT, AsrState.CLOSED);

        // RECOGNIZING
        addTransition(AsrState.RECOGNIZING, AsrEvent.RECEIVE_AUDIO, AsrState.RECOGNIZING);
        addTransition(AsrState.RECOGNIZING, AsrEvent.CLIENT_RESULT, AsrState.RECOGNIZING);
        addTransition(AsrState.RECOGNIZING, AsrEvent.STOP_TRANSCRIPTION, AsrState.STOPPING);
        addTransition(AsrState.RECOGNIZING, AsrEvent.DISCONNECT, AsrState.CLOSED);
        addTransition(AsrState.RECOGNIZING, AsrEvent.ERROR, AsrState.CLOSED);

        // STOPPING
        addTransition(AsrState.STOPPING, AsrEvent.CLIENT_RESULT, AsrState.STOPPING);
        addTransition(AsrState.STOPPING, AsrEvent.DISCONNECT, AsrState.CLOSED);
        addTransition(AsrState.STOPPING, AsrEvent.ERROR, AsrState.CLOSED);
    }

    private void addTransition(AsrState from, AsrEvent event, AsrState to) {
        transitionTable
                .computeIfAbsent(from, k -> new EnumMap<>(AsrEvent.class))
                .put(event, new Transition(to));
    }

    /**
     * 触发事件，仅做状态转换（在锁内），不执行业务动作。
     * @return 转换成功返回 true
     */
    public synchronized boolean fire(AsrEvent event) {
        Map<AsrEvent, Transition> events = transitionTable.get(currentState);
        if (events == null) {
            log.warn("ASR状态机: 状态 {} 无任何转换规则, 忽略事件 {}", currentState, event);
            return false;
        }
        Transition transition = events.get(event);
        if (transition == null) {
            log.warn("ASR状态机: 状态 {} 不接受事件 {}, 忽略", currentState, event);
            return false;
        }
        AsrState fromState = currentState;
        currentState = transition.targetState;
        if (fromState != currentState) {
            log.info("ASR状态机: {} --[{}]--> {}", fromState, event, currentState);
        }
        return true;
    }

    public synchronized AsrState getCurrentState() {
        return currentState;
    }

    public synchronized boolean isState(AsrState state) {
        return currentState == state;
    }

    private static class Transition {
        final AsrState targetState;

        Transition(AsrState targetState) {
            this.targetState = targetState;
        }
    }

}
