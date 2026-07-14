package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.HealthResponseDto;
import com.confluence.ingestor.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public HealthResponseDto health() {
        return healthService.getHealth();
    }
}
