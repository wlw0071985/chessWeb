package com.chessweb.dictation.model;

import java.util.ArrayList;
import java.util.List;

public class ReviewWord {

    private String id;
    private String word;
    private String phonetic;
    private String chinese;
    private List<ExamplePair> examples = new ArrayList<ExamplePair>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getChinese() {
        return chinese;
    }

    public void setChinese(String chinese) {
        this.chinese = chinese;
    }

    public List<ExamplePair> getExamples() {
        return examples;
    }

    public void setExamples(List<ExamplePair> examples) {
        this.examples = examples;
    }
}
