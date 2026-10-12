package com.chessweb.dictation;

import com.chessweb.dictation.model.ExamplePair;
import com.chessweb.dictation.model.WordItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class ExampleService {

    private static final Logger log = LoggerFactory.getLogger(ExampleService.class);
    private static final TypeReference<List<ExamplePair>> EXAMPLE_LIST =
            new TypeReference<List<ExamplePair>>() {
            };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final long exampleTtlDays;

    public ExampleService(StringRedisTemplate redis,
                          ObjectMapper objectMapper,
                          @Value("${dictation.example-ttl-days:7}") long exampleTtlDays) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.exampleTtlDays = exampleTtlDays;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<ExamplePair> examplesFor(WordItem word) {
        String cacheKey = "dictation:examples:" + normalize(word.getWord());
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, EXAMPLE_LIST);
            }
        } catch (Exception e) {
            log.warn("read example cache failed: {}", e.getMessage());
        }

        List<ExamplePair> pairs = fetchExamplesFromDictionary(word.getWord());
        if (pairs == null || pairs.isEmpty()) {
            pairs = new ArrayList<ExamplePair>();
            pairs.add(new ExamplePair(fallbackSentence(word.getWord(), 0), translateToChinese(fallbackSentence(word.getWord(), 0))));
            pairs.add(new ExamplePair(fallbackSentence(word.getWord(), 1), translateToChinese(fallbackSentence(word.getWord(), 1))));
        }
        if (pairs.size() > 2) {
            pairs = new ArrayList<ExamplePair>(pairs.subList(0, 2));
        }

        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(pairs),
                    Duration.ofDays(exampleTtlDays).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("write example cache failed: {}", e.getMessage());
        }
        return pairs;
    }

    private List<ExamplePair> fetchExamplesFromDictionary(String word) {
        List<ExamplePair> pairs = collectExamplesFromYoudao(word);
        if (pairs != null && !pairs.isEmpty()) {
            return pairs;
        }
        log.info("Youdao dictionary API failed or returned no examples, trying fallback for: {}", word);
        pairs = collectExamplesFromFallback(word);
        if (pairs != null && !pairs.isEmpty()) {
            return pairs;
        }
        return null;
    }

    private List<ExamplePair> collectExamplesFromFallback(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.dictionaryapi.dev/api/v2/entries/en/{word}")
                    .buildAndExpand(query)
                    .encode()
                    .toUri();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");
            
            RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.isArray()) {
                return null;
            }
            
            List<ExamplePair> pairs = new ArrayList<ExamplePair>();
            for (JsonNode entry : root) {
                JsonNode meanings = entry.path("meanings");
                for (JsonNode meaning : meanings) {
                    JsonNode definitions = meaning.path("definitions");
                    for (JsonNode def : definitions) {
                        JsonNode example = def.get("example");
                        if (example != null && example.isTextual()) {
                            String text = example.asText().trim();
                            if (!text.isEmpty() && !containsIgnoreCase(pairs, text)) {
                                pairs.add(new ExamplePair(text, translateToChinese(text)));
                                if (pairs.size() >= 2) {
                                    return pairs;
                                }
                            }
                        }
                    }
                }
            }
            return pairs.isEmpty() ? null : pairs;
        } catch (Exception e) {
            log.warn("Fallback dictionary API failed for {}: {}", query, e.getMessage());
            return null;
        }
    }

    private List<ExamplePair> collectExamplesFromYoudao(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://dict.youdao.com/jsonapi?q={word}")
                    .buildAndExpand(query)
                    .encode()
                    .toUri();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");
            
            RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            
            List<ExamplePair> pairs = new ArrayList<ExamplePair>();
            JsonNode sentencePair = root.path("blng_sents_part").path("sentence-pair");
            if (sentencePair.isArray()) {
                for (JsonNode pair : sentencePair) {
                    String engSent = pair.path("sentence").asText("").trim();
                    String chnSent = pair.path("sentence-translation").asText("").trim();
                    if (!engSent.isEmpty() && !chnSent.isEmpty() && !containsIgnoreCase(pairs, engSent)) {
                        pairs.add(new ExamplePair(engSent, chnSent));
                        if (pairs.size() >= 2) {
                            return pairs;
                        }
                    }
                }
            }
            return pairs.isEmpty() ? null : pairs;
        } catch (Exception e) {
            log.warn("Youdao dictionary API failed for {}: {}", query, e.getMessage());
            return null;
        }
    }

    private String translateToChinese(String english) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.mymemory.translated.net/get")
                    .queryParam("q", english)
                    .queryParam("langpair", "en|zh-CN")
                    .build()
                    .encode()
                    .toUri();
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getBody() == null) {
                return "（暂无中文翻译）";
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String translated = root.path("responseData").path("translatedText").asText("");
            if (translated.isEmpty() || translated.toLowerCase(Locale.ROOT).contains("query limit")) {
                return "（暂无中文翻译）";
            }
            return translated;
        } catch (Exception e) {
            log.info("translate failed: {}", e.getMessage());
            return "（暂无中文翻译）";
        }
    }

    private static String fallbackSentence(String word, int index) {
        if (index == 0) {
            return "I learned the word \"" + word + "\" in English class.";
        }
        return "Please write a sentence with \"" + word + "\".";
    }

    private static boolean containsIgnoreCase(List<ExamplePair> list, String text) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getEnglish().equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    public byte[] ttsAudioBytes(String text) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("http://127.0.0.1:5050/")
                    .queryParam("text", text)
                    .build()
                    .toUri();
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(uri, byte[].class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("TTS fetch failed for '{}': {}", text, e.getMessage());
            return null;
        }
    }

    /**
     * 通过 api.dictionaryapi.dev 获取单词或句子的音标
     * 如果是句子，会拆分成单词逐个查询音标后拼接
     */
    public String fetchPhonetic(String word) {
        if (word == null || word.trim().isEmpty()) {
            return null;
        }
        
        String input = word.trim();
        
        // 判断是否是句子（包含空格和多个单词）
        if (input.contains(" ")) {
            return fetchPhoneticForSentence(input);
        }
        
        // 单个单词查询
        return fetchPhoneticForSingleWord(input);
    }
    
    /**
     * 获取句子的音标：拆分成单词，逐个查询后拼接
     */
    private String fetchPhoneticForSentence(String sentence) {
        String[] words = sentence.split("\\s+");
        StringBuilder phoneticBuilder = new StringBuilder();
        int successCount = 0; // 记录成功获取音标的单词数量
        
        for (int i = 0; i < words.length; i++) {
            String w = words[i].replaceAll("[.,!?;:\"']", ""); // 去除标点
            if (w.isEmpty()) {
                continue;
            }
            
            String phonetic = fetchPhoneticForSingleWord(w);
            if (phonetic != null && !phonetic.isEmpty()) {
                if (phoneticBuilder.length() > 0) {
                    phoneticBuilder.append(" ");
                }
                // 统一音标格式：去除斜杠
                String cleanPhonetic = phonetic.replaceAll("^/|/$", "");
                phoneticBuilder.append(cleanPhonetic);
                successCount++;
                log.debug("Word '{}' phonetic: {}", w, cleanPhonetic);
            } else {
                // 如果某个单词无法获取音标，保留原单词
                if (phoneticBuilder.length() > 0) {
                    phoneticBuilder.append(" ");
                }
                phoneticBuilder.append(w);
                log.debug("Word '{}' phonetic not found, using original", w);
            }
        }
        
        // 如果没有任何单词成功获取音标，返回 null
        if (successCount == 0) {
            log.warn("Failed to get phonetics for any word in sentence: {}", sentence);
            return null;
        }
        
        return phoneticBuilder.length() > 0 ? phoneticBuilder.toString() : null;
    }
    
    /**
     * 获取单个单词的音标（使用有道词典 API）
     */
    private String fetchPhoneticForSingleWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://dict.youdao.com/jsonapi?q={word}")
                    .buildAndExpand(word.trim())
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");

            RequestEntity<Void> request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            
            // 按照路径获取音标：simple -> simple.word[0] -> usphone
            
            // 1. 尝试从 simple 获取
            JsonNode simple = root.path("simple");
            if (!simple.isMissingNode() && simple.isObject()) {
                // 2. 从 simple.word 数组获取
                JsonNode wordArray = simple.path("word");
                if (!wordArray.isMissingNode() && wordArray.isArray() && wordArray.size() > 0) {
                    JsonNode firstWord = wordArray.get(0);
                    
                    // 3. 从 word[0] 中获取 usphone
                    JsonNode usphone = firstWord.path("usphone");
                    if (!usphone.isMissingNode() && usphone.isTextual()) {
                        String text = usphone.asText("").trim();
                        if (!text.isEmpty()) {
                            // 如果有分号，取最后一个部分
                            if (text.contains(";")) {
                                String[] parts = text.split(";");
                                text = parts[parts.length - 1].trim();
                            }
                            return text.replaceAll("^/|/$", "");
                        }
                    }
                    
                    // 备选：尝试 ukphone
                    JsonNode ukphone = firstWord.path("ukphone");
                    if (!ukphone.isMissingNode() && ukphone.isTextual()) {
                        String text = ukphone.asText("").trim();
                        if (!text.isEmpty()) {
                            // 如果有分号，取最后一个部分
                            if (text.contains(";")) {
                                String[] parts = text.split(";");
                                text = parts[parts.length - 1].trim();
                            }
                            return text.replaceAll("^/|/$", "");
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("fetch phonetic failed for '{}': {}", word, e.getMessage());
            return null;
        }
    }

    /**
     * 通过 MyMemory 翻译 API 获取单词的中文释义
     */
    public String fetchChineseMeaning(String word) {
        if (word == null || word.trim().isEmpty()) {
            return null;
        }
        String translated = translateToChinese(word.trim());
        if (translated == null || translated.isEmpty() || "（暂无中文翻译）".equals(translated)) {
            return null;
        }
        return translated;
    }

    private static String normalize(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
    }
}
