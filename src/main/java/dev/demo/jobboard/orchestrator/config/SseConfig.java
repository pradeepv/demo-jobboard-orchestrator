package dev.demo.jobboard.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.demo.jobboard.orchestrator.sse.SseEventBus;

@Configuration
public class SseConfig {

    @Bean
    public SseEventBus sseEventBus() {
        // Default: 30 min timeout, 20s heartbeat
        return new SseEventBus();
    }
}