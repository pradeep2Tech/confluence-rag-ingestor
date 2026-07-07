package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.ChatRequest;
import com.confluence.ingestor.api.dto.ChatResponse;
import com.confluence.ingestor.api.dto.ChatSource;
import com.confluence.ingestor.api.dto.ChatStatus;
import com.confluence.ingestor.api.dto.QueryHit;
import com.confluence.ingestor.api.dto.QueryRequest;
import com.confluence.ingestor.api.dto.QueryResponse;
import com.confluence.ingestor.api.dto.QueryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG chat — retrieves relevant chunks then asks the configured LLM to answer using that context.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final QueryService queryService;
    private final ObjectProvider<ChatModel> chatModel;
    private final RuntimeConfigService runtimeConfigService;

    public ChatService(
            QueryService queryService,
            ObjectProvider<ChatModel> chatModel,
            RuntimeConfigService runtimeConfigService) {
        this.queryService = queryService;
        this.chatModel = chatModel;
        this.runtimeConfigService = runtimeConfigService;
    }

    public ChatResponse chat(ChatRequest request) {
        String question = request.question().strip();
        String parentPageId = request.normalizedParentPageId();
        if (parentPageId == null) {
            String configured = runtimeConfigService.currentConfig().parentPageId();
            parentPageId = configured != null && !configured.isBlank() ? configured : null;
        }

        QueryResponse retrieval = queryService.query(new QueryRequest(
                question,
                parentPageId,
                request.topK(),
                request.similarityThreshold()));

        if (retrieval.status() != QueryStatus.SUCCESS) {
            return ChatResponse.error(question, parentPageId, retrieval.message(), retrieval.errorDetail());
        }

        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return buildRetrievalOnlyResponse(question, parentPageId, retrieval);
        }

        try {
            String context = buildContext(retrieval.hits());
            String systemPrompt = """
                    You are a helpful assistant answering questions about Confluence documentation.
                    Use ONLY the provided context to answer. If the context does not contain enough information,
                    say you do not have enough information in the ingested content.
                    Cite page titles when relevant. Be concise and accurate.
                    """;
            String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question;

            String answer = ChatClient.create(model)
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            List<ChatSource> sources = toSources(retrieval.hits());
            log.info("Chat completed parentPageId={} sources={}", parentPageId, sources.size());
            return ChatResponse.success(question, parentPageId, answer, sources);
        } catch (Exception ex) {
            log.warn("Chat generation failed: {}", ex.getMessage());
            return ChatResponse.error(question, parentPageId, "Chat generation failed", ex.getMessage());
        }
    }

    private ChatResponse buildRetrievalOnlyResponse(String question, String parentPageId, QueryResponse retrieval) {
        if (retrieval.hits().isEmpty()) {
            return ChatResponse.error(
                    question,
                    parentPageId,
                    "No relevant content found",
                    "Ingest content first or start Ollama with a chat model");
        }
        String answer = retrieval.hits().stream()
                .map(hit -> "• " + formatHitSummary(hit) + "\n" + truncate(hit.text(), 500))
                .collect(Collectors.joining("\n\n"));
        return ChatResponse.success(
                question,
                parentPageId,
                "LLM chat model is not configured. Showing top matching excerpts:\n\n" + answer,
                toSources(retrieval.hits()));
    }

    private static String buildContext(List<QueryHit> hits) {
        if (hits.isEmpty()) {
            return "(no relevant context found)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            QueryHit hit = hits.get(i);
            builder.append("--- Source ")
                    .append(i + 1)
                    .append(" ---\n");
            if (hit.title() != null) {
                builder.append("Page: ").append(hit.title()).append('\n');
            }
            if (hit.headingPath() != null) {
                builder.append("Section: ").append(hit.headingPath()).append('\n');
            }
            if (hit.webUrl() != null) {
                builder.append("URL: ").append(hit.webUrl()).append('\n');
            }
            builder.append(hit.text()).append("\n\n");
        }
        return builder.toString();
    }

    private static List<ChatSource> toSources(List<QueryHit> hits) {
        List<ChatSource> sources = new ArrayList<>();
        for (QueryHit hit : hits) {
            sources.add(new ChatSource(
                    hit.title(),
                    hit.webUrl(),
                    hit.headingPath(),
                    hit.score(),
                    truncate(hit.text(), 300)));
        }
        return sources;
    }

    private static String formatHitSummary(QueryHit hit) {
        String title = hit.title() != null ? hit.title() : "Untitled";
        if (hit.headingPath() != null && !hit.headingPath().isBlank()) {
            return title + " › " + hit.headingPath();
        }
        return title;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
