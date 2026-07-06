package com.confluence.ingestor.confluence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluencePageBody {

    private ConfluenceStorageBody storage;

    @JsonProperty("view")
    private ConfluenceStorageBody view;

    public ConfluenceStorageBody getStorage() {
        return storage;
    }

    public void setStorage(ConfluenceStorageBody storage) {
        this.storage = storage;
    }

    public ConfluenceStorageBody getView() {
        return view;
    }

    public void setView(ConfluenceStorageBody view) {
        this.view = view;
    }

    public String storageHtml() {
        if (storage == null || storage.getValue() == null) {
            return "";
        }
        return storage.getValue();
    }
}
