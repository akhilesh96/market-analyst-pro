package com.javaproject.controller;

import com.javaproject.service.AnalysisStreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {
    @Autowired
    private AnalysisStreamService streamService;

    @GetMapping(value = "/api/analysis-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAIReports() {
        return streamService.createEmitter();
    }
}
