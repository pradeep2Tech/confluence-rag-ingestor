package com.confluence.ingestor.confluence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluenceChildListResponse {

    private List<ConfluencePageDto> results = new ArrayList<>();

    @JsonProperty("_links")
    private Map<String, String> links;

    public List<ConfluencePageDto> getResults() {
        return results != null ? results : List.of();
    }

    public void setResults(List<ConfluencePageDto> results) {
        this.results = results;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    public String nextLink() {
        if (links == null) {
            return null;
        }
        return links.get("next");
    }
}
