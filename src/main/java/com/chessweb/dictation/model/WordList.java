package com.chessweb.dictation.model;

import java.util.ArrayList;
import java.util.List;

public class WordList {

    private String listId;
    private String unitId;
    private String unitTitle;
    private String listTitle;
    private List<WordItem> words = new ArrayList<WordItem>();

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getUnitTitle() {
        return unitTitle;
    }

    public void setUnitTitle(String unitTitle) {
        this.unitTitle = unitTitle;
    }

    public String getListTitle() {
        return listTitle;
    }

    public void setListTitle(String listTitle) {
        this.listTitle = listTitle;
    }

    public List<WordItem> getWords() {
        return words;
    }

    public void setWords(List<WordItem> words) {
        this.words = words;
    }
}
