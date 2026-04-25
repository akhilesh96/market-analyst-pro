package com.javaproject.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AnalysisStreamService {
    // List of active browser connections
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));

        return emitter;
    }

    public void broadcast(String analysis) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ai-report").data(analysis));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
