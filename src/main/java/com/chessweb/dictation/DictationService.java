package com.chessweb.dictation;

import com.chessweb.dictation.model.ReviewWord;
import com.chessweb.dictation.model.WordItem;
import com.chessweb.dictation.model.WordList;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DictationService {

    private static final TypeReference<List<WordItem>> WORD_LIST =
            new TypeReference<List<WordItem>>() {
            };

    private final VocabLoader vocabLoader;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ExampleService exampleService;
    private final long selectionTtlHours;

    public DictationService(VocabLoader vocabLoader,
                            StringRedisTemplate redis,
                            ObjectMapper objectMapper,
                            ExampleService exampleService,
                            @Value("${dictation.selection-ttl-hours:12}") long selectionTtlHours) {
        this.vocabLoader = vocabLoader;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.exampleService = exampleService;
        this.selectionTtlHours = selectionTtlHours;
    }

    public List<WordList> catalog() {
        return vocabLoader.getLists();
    }

    public List<WordItem> saveSelection(String clientId, List<String> wordIds) {
        requireClient(clientId);
        List<WordItem> selected = vocabLoader.resolveInCatalogOrder(wordIds);
        if (selected.isEmpty()) {
            throw new DictationException("请至少勾选一个单词");
        }
        writeJson(selectionKey(clientId), selected);
        return selected;
    }

    public List<WordItem> getSelection(String clientId) {
        requireClient(clientId);
        return readWords(selectionKey(clientId));
    }

    public List<WordItem> start(String clientId) {
        requireClient(clientId);
        List<WordItem> selected = readWords(selectionKey(clientId));
        if (selected.isEmpty()) {
            throw new DictationException("没有已确认的听写范围，或记录已过期，请重新勾选单词");
        }
        List<WordItem> shuffled = new ArrayList<WordItem>(selected);
        Collections.shuffle(shuffled);
        writeJson(practiceKey(clientId), shuffled);
        return shuffled;
    }

    public List<WordItem> getPractice(String clientId) {
        requireClient(clientId);
        List<WordItem> practice = readWords(practiceKey(clientId));
        if (practice.isEmpty()) {
            throw new DictationException("没有进行中的听写，或记录已过期，请重新开始");
        }
        return practice;
    }

    public List<ReviewWord> review(String clientId) {
        List<WordItem> practice = getPractice(clientId);
        List<ReviewWord> review = new ArrayList<ReviewWord>();
        for (WordItem item : practice) {
            ReviewWord row = new ReviewWord();
            row.setId(item.getId());
            row.setWord(item.getWord());
            row.setPhonetic(item.getPhonetic());
            row.setChinese(item.getChinese());
            row.setExamples(exampleService.examplesFor(item));
            review.add(row);
        }
        return review;
    }

    private void writeJson(String key, List<WordItem> words) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(words),
                    selectionTtlHours, TimeUnit.HOURS);
        } catch (DictationException e) {
            throw e;
        } catch (Exception e) {
            throw new DictationException("无法连接 Redis（127.0.0.1:6379），请确认本地 Redis 已启动");
        }
    }

    private List<WordItem> readWords(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return Collections.emptyList();
            }
            List<WordItem> words = objectMapper.readValue(json, WORD_LIST);
            return words == null ? Collections.<WordItem>emptyList() : words;
        } catch (DictationException e) {
            throw e;
        } catch (Exception e) {
            throw new DictationException("无法连接 Redis（127.0.0.1:6379），请确认本地 Redis 已启动");
        }
    }

    private static void requireClient(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new DictationException("缺少 clientId");
        }
    }

    private static String selectionKey(String clientId) {
        return "dictation:selection:" + clientId.trim();
    }

    private static String practiceKey(String clientId) {
        return "dictation:practice:" + clientId.trim();
    }
}
