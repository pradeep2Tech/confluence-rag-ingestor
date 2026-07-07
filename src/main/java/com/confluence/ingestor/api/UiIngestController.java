package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.IngestionMode;
import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.api.dto.IngestionResponse;
import com.confluence.ingestor.api.dto.IngestionStatus;
import com.confluence.ingestor.api.dto.UiIngestRequest;
import com.confluence.ingestor.model.RuntimeConfig;
import com.confluence.ingestor.service.IngestionService;
import com.confluence.ingestor.service.RuntimeConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard-friendly ingestion endpoints backed by saved runtime configuration.
 */
@RestController
@RequestMapping("/api/ingest")
public class UiIngestController {

    private final RuntimeConfigService runtimeConfigService;
    private final IngestionService ingestionService;

    public UiIngestController(RuntimeConfigService runtimeConfigService, IngestionService ingestionService) {
        this.runtimeConfigService = runtimeConfigService;
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<IngestionResponse> startIngestion(@Valid @RequestBody(required = false) UiIngestRequest request) {
        UiIngestRequest resolved = request != null ? request : new UiIngestRequest(null, null, null, null, null, null);
        if (!runtimeConfigService.isReadyForIngestion()) {
            IngestionResponse error = IngestionResponse.error(
                    IngestionMode.MANIFEST_INIT,
                    runtimeConfigService.currentConfig().parentPageId(),
                    null,
                    "Configuration incomplete",
                    "Save Confluence base URL, target page, and PAT first");
            return ResponseEntity.badRequest().body(error);
        }

        RuntimeConfig config = runtimeConfigService.currentConfig();
        IngestionRequest ingestionRequest = new IngestionRequest(
                config.confluenceBaseUrl(),
                config.parentPageId(),
                runtimeConfigService.currentPat(),
                resolved.shouldForceRebuildManifest(),
                resolved.shouldExtractMarkdown(),
                resolved.shouldChunkMarkdown(),
                resolved.shouldIngestVectors(),
                resolved.batchSize(),
                resolved.concurrency(),
                null);

        IngestionResponse response = ingestionService.startIngestion(ingestionRequest);
        HttpStatus httpStatus = switch (response.status()) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case ALREADY_RUNNING -> HttpStatus.CONFLICT;
            case ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(httpStatus).body(response);
    }

    @GetMapping("/status")
    public ResponseEntity<IngestionStatus> status(@RequestParam(required = false) String parentPageId) {
        String resolvedParentPageId = parentPageId;
        if (resolvedParentPageId == null || resolvedParentPageId.isBlank()) {
            resolvedParentPageId = runtimeConfigService.currentConfig().parentPageId();
        }
        if (resolvedParentPageId == null || resolvedParentPageId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ingestionService.getStatus(resolvedParentPageId));
    }
}
