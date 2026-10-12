package com.chessweb.dictation;

import com.chessweb.dictation.model.WordItem;
import com.chessweb.dictation.model.WordList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VocabLoader {

    private static final String PATTERN = "classpath:vocab/fltrp-g7a/**/*.json";
    private static final String VOCAB_BASE_PATH = "src/main/resources/vocab/fltrp-g7a";

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;
    private final Map<String, Path> listIdToFileMap = new ConcurrentHashMap<String, Path>();
    private final List<WordList> lists = new ArrayList<WordList>();
    private final Map<String, WordItem> wordsById = new LinkedHashMap<String, WordItem>();
    private final List<WordItem> orderedWords = new ArrayList<WordItem>();
    private final String projectRoot;

    public VocabLoader(ObjectMapper objectMapper,
                       @Value("${project.basedir:#{null}}") String projectBasedir) {
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
        
        if (projectBasedir != null && !projectBasedir.isEmpty()) {
            this.projectRoot = projectBasedir;
        } else {
            this.projectRoot = System.getProperty("user.dir");
        }
    }

    @PostConstruct
    public void load() throws Exception {
        this.buildListIdToFileMap();
        
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

    private void buildListIdToFileMap() throws Exception {
        Resource[] resources = resourceResolver.getResources(PATTERN);
        for (Resource resource : resources) {
            InputStream in = null;
            try {
                in = resource.getInputStream();
                WordList wl = objectMapper.readValue(in, WordList.class);
                if (wl != null && wl.getListId() != null) {
                    String filename = resource.getFilename();
                    String unitId = wl.getUnitId();
                    String relativePath = VOCAB_BASE_PATH + "/" + unitId + "/" + filename;
                    Path filePath = Paths.get(projectRoot, relativePath);
                    listIdToFileMap.put(wl.getListId(), filePath);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
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

    /**
     * 根据 word 字段查找对应的 WordItem（精确匹配，忽略大小写）
     */
    public WordItem findByWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return null;
        }
        String target = word.trim().toLowerCase(Locale.ROOT);
        for (WordItem item : orderedWords) {
            if (target.equals(item.getWord().toLowerCase(Locale.ROOT))) {
                return item;
            }
        }
        return null;
    }

    /**
     * 更新 WordItem 的 phonetic 和 chinese 字段（内存和文件都生效）
     */
    public synchronized void updateWordItem(String id, String phonetic, String chinese) {
        WordItem item = wordsById.get(id);
        if (item == null) {
            return;
        }
        boolean changed = false;
        // 只有当新值不为空时才更新
        if (phonetic != null && !phonetic.isEmpty() && !phonetic.equals(item.getPhonetic())) {
            item.setPhonetic(phonetic);
            changed = true;
        }
        if (chinese != null && !chinese.isEmpty() && !chinese.equals(item.getChinese())) {
            item.setChinese(chinese);
            changed = true;
        }
        if (!changed) {
            return;
        }
        
        // 找到该 WordItem 所属的 listId，然后写回文件
        String listId = findListIdById(id);
        if (listId != null) {
            writeListToFile(listId);
        }
    }

    private String findListIdById(String id) {
        for (WordList list : lists) {
            if (list.getWords() != null) {
                for (WordItem item : list.getWords()) {
                    if (id.equals(item.getId())) {
                        return list.getListId();
                    }
                }
            }
        }
        return null;
    }

    private void writeListToFile(String listId) {
        Path filePath = listIdToFileMap.get(listId);
        if (filePath == null) {
            System.err.println("Cannot find file path for listId: " + listId);
            return;
        }
        try {
            WordList targetList = null;
            for (WordList list : lists) {
                if (listId.equals(list.getListId())) {
                    targetList = list;
                    break;
                }
            }
            if (targetList == null) {
                return;
            }
            
            File file = filePath.toFile();
            if (!file.exists()) {
                System.err.println("File does not exist: " + filePath);
                return;
            }
            
            objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targetList);
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(json);
            writer.flush();
            writer.close();
            System.out.println("Successfully wrote vocab file: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to write vocab file for " + listId + ": " + e.getMessage());
            e.printStackTrace();
        }
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
