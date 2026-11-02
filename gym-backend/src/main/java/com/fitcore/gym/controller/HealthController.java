package com.fitcore.gym.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final com.fitcore.gym.operations.ReleaseReadiness readiness;
    public HealthController(com.fitcore.gym.operations.ReleaseReadiness readiness) { this.readiness=readiness; }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String,String>> ready() {
        boolean ready=readiness.ready();
        return ResponseEntity.status(ready ? 200 : 503).body(Map.of("status",ready ? "READY" : "NOT_READY"));
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
