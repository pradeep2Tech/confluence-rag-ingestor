package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.api.dto.IngestionResponse;
import com.confluence.ingestor.api.dto.IngestionStatus;
import com.confluence.ingestor.service.IngestionService;
import io.micrometer.observation.annotation.Observed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/confluence/ingest")
    @Observed(name = "api.ingest")
    public ResponseEntity<IngestionResponse> ingest(@Valid @RequestBody IngestionRequest request) {
        log.info(
                "Ingest request parentPageId={} forceRebuild={} extractMarkdown={} chunkMarkdown={} ingestVectors={}",
                request.parentPageId(),
                request.shouldForceRebuildManifest(),
                request.shouldExtractMarkdown(),
                request.shouldChunkMarkdown(),
                request.shouldIngestVectors());
        IngestionResponse response = ingestionService.startIngestion(request);
        log.info(
                "Ingest response parentPageId={} status={} mode={}",
                request.parentPageId(),
                response.status(),
                response.mode());
        HttpStatus httpStatus = switch (response.status()) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case ALREADY_RUNNING -> HttpStatus.CONFLICT;
            case ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(httpStatus).body(response);
    }

    @GetMapping("/api/confluence/ingest/status/{parentPageId}")
    @Observed(name = "api.ingest.status")
    public IngestionStatus status(@PathVariable String parentPageId) {
        log.debug("Status request parentPageId={}", parentPageId);
        return ingestionService.getStatus(parentPageId);
    }
}
