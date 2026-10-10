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

    private static String normalize(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
    }
}
