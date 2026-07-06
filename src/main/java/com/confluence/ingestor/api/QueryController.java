package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.QueryRequest;
import com.confluence.ingestor.api.dto.QueryResponse;
import com.confluence.ingestor.api.dto.QueryStatus;
import com.confluence.ingestor.service.QueryService;
import io.micrometer.observation.annotation.Observed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Semantic search over ingested Confluence chunks (no PAT — local ChromaDB only).
 */
@RestController
@RequestMapping
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/api/confluence/query")
    @Observed(name = "api.query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.info(
                "Query request parentPageId={} topK={} queryLength={}",
                request.normalizedParentPageId(),
                request.topK(),
                request.query() != null ? request.query().length() : 0);
        QueryResponse response = queryService.query(request);
        log.info("Query response status={} hits={}", response.status(), response.resultCount());
        HttpStatus status = response.status() == QueryStatus.SUCCESS ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
