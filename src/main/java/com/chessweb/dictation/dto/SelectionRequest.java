package com.chessweb.dictation.dto;

import java.util.ArrayList;
import java.util.List;

public class SelectionRequest {

    private String clientId;
    private List<String> wordIds = new ArrayList<String>();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getWordIds() {
        return wordIds;
    }

    public void setWordIds(List<String> wordIds) {
        this.wordIds = wordIds;
    }
}
