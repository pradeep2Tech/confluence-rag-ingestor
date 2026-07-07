package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.ConfigRequest;
import com.confluence.ingestor.api.dto.ConfigResponse;
import com.confluence.ingestor.service.RuntimeConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final RuntimeConfigService runtimeConfigService;

    public ConfigController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping
    public ConfigResponse getConfig() {
        return runtimeConfigService.getMaskedConfig();
    }

    @PostMapping
    public ConfigResponse saveConfig(@Valid @RequestBody ConfigRequest request) {
        return runtimeConfigService.save(request);
    }
}
