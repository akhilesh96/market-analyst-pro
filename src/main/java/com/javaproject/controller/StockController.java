package com.javaproject.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    // The original POST mapping for forms/Postman/cURL
    @PostMapping("/analyze/{ticker}")
    public ResponseEntity<String> startAnalysisPost(@PathVariable String ticker) {
        return performTrigger(ticker);
    }

    // NEW: The GET mapping so you can just type it in the browser
    @GetMapping("/analyze/{ticker}")
    public ResponseEntity<String> startAnalysisGet(@PathVariable String ticker) {
        return performTrigger(ticker);
    }

    private ResponseEntity<String> performTrigger(String ticker) {
        String command = String.format("{\"action\": \"START\", \"ticker\": \"%s\"}", ticker);
        kafkaTemplate.send("stock-command", command);
        return ResponseEntity.ok("Command sent successfully for: " + ticker);
    }
}