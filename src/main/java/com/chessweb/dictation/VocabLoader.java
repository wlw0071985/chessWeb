package com.chessweb.dictation;

import com.chessweb.dictation.model.WordItem;
import com.chessweb.dictation.model.WordList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VocabLoader {

    private static final String PATTERN = "classpath:vocab/fltrp-g7a/**/*.json";

    private final ObjectMapper objectMapper;
    private final List<WordList> lists = new ArrayList<WordList>();
    private final Map<String, WordItem> wordsById = new LinkedHashMap<String, WordItem>();
    private final List<WordItem> orderedWords = new ArrayList<WordItem>();

    public VocabLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(PATTERN);
        List<WordList> loaded = new ArrayList<WordList>();
        for (int i = 0; i < resources.length; i++) {
            Resource resource = resources[i];
            InputStream in = resource.getInputStream();
            try {
                loaded.add(objectMapper.readValue(in, WordList.class));
            } finally {
                in.close();
            }
        }
        Collections.sort(loaded, new Comparator<WordList>() {
            @Override
            public int compare(WordList a, WordList b) {
                int unit = Integer.compare(unitOrder(a.getUnitId()), unitOrder(b.getUnitId()));
                if (unit != 0) {
                    return unit;
                }
                return nullToEmpty(a.getListId()).compareTo(nullToEmpty(b.getListId()));
            }
        });
        lists.clear();
        lists.addAll(loaded);
        wordsById.clear();
        orderedWords.clear();
        for (WordList list : lists) {
            if (list.getWords() == null) {
                continue;
            }
            for (WordItem word : list.getWords()) {
                wordsById.put(word.getId(), word);
                orderedWords.add(word);
            }
        }
    }

    public List<WordList> getLists() {
        return Collections.unmodifiableList(lists);
    }

    public List<WordItem> resolveInCatalogOrder(List<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.HashSet<String> wanted = new java.util.HashSet<String>(wordIds);
        List<WordItem> result = new ArrayList<WordItem>();
        for (WordItem word : orderedWords) {
            if (wanted.contains(word.getId())) {
                result.add(word);
            }
        }
        return result;
    }

    private static int unitOrder(String unitId) {
        if ("starter".equals(unitId)) {
            return 0;
        }
        if (unitId != null && unitId.startsWith("unit-")) {
            try {
                return Integer.parseInt(unitId.substring(5));
            } catch (NumberFormatException ignored) {
                return 99;
            }
        }
        return 99;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
