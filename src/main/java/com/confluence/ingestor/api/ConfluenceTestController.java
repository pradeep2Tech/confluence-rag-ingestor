package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.ConfluenceTestRequest;
import com.confluence.ingestor.api.dto.ConfluenceTestResponse;
import com.confluence.ingestor.service.ConfluenceConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/confluence")
public class ConfluenceTestController {

    private final ConfluenceConnectionService connectionService;

    public ConfluenceTestController(ConfluenceConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/test")
    public ResponseEntity<ConfluenceTestResponse> testConnection(@Valid @RequestBody ConfluenceTestRequest request) {
        ConfluenceTestResponse response = connectionService.testConnection(
                request.baseUrl(), request.pat(), request.confluenceTarget(), request.verifySsl());
        HttpStatus status = response.connected() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}
