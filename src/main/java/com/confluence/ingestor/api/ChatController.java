package com.confluence.ingestor.api;

import com.confluence.ingestor.api.dto.ChatRequest;
import com.confluence.ingestor.api.dto.ChatResponse;
import com.confluence.ingestor.api.dto.ChatStatus;
import com.confluence.ingestor.service.ChatService;
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

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Observed(name = "api.chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request parentPageId={} questionLength={}", request.normalizedParentPageId(), request.question().length());
        ChatResponse response = chatService.chat(request);
        HttpStatus status = response.status() == ChatStatus.SUCCESS ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
