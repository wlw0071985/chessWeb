package com.chessweb.dictation.dto;

import java.util.ArrayList;
import java.util.List;

public class AnswerRequest {

    private String clientId;
    private int index;
    private List<String> answers = new ArrayList<String>();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }
}