package com.chessweb.dictation;

import com.chessweb.dictation.dto.AnswerRequest;
import com.chessweb.dictation.dto.ClientRequest;
import com.chessweb.dictation.dto.SelectionRequest;
import com.chessweb.dictation.model.ExamplePair;
import com.chessweb.dictation.model.ReviewWord;
import com.chessweb.dictation.model.WordItem;
import com.chessweb.dictation.model.WordList;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictation")
public class DictationController {

    private final DictationService dictationService;
    private final ExampleService exampleService;

    public DictationController(DictationService dictationService, ExampleService exampleService) {
        this.dictationService = dictationService;
        this.exampleService = exampleService;
    }

    @GetMapping("/catalog")
    public List<WordList> catalog() {
        return dictationService.catalog();
    }

    @PostMapping("/selection")
    public ResponseEntity<Map<String, Object>> saveSelection(@RequestBody SelectionRequest request) {
        List<WordItem> selected = dictationService.saveSelection(request.getClientId(), request.getWordIds());
        return ResponseEntity.ok(wrapWords("selected", selected));
    }

    @GetMapping("/selection")
    public Map<String, Object> getSelection(@RequestParam("clientId") String clientId) {
        return wrapWords("selected", dictationService.getSelection(clientId));
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody ClientRequest request) {
        return wrapWords("words", dictationService.start(request.getClientId()));
    }

    @GetMapping("/practice")
    public Map<String, Object> practice(@RequestParam("clientId") String clientId) {
        return wrapWords("words", dictationService.getPractice(clientId));
    }

    @GetMapping("/review")
    public Map<String, Object> review(@RequestParam("clientId") String clientId) {
        List<ReviewWord> review = dictationService.review(clientId);
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("words", review);
        body.put("total", review.size());
        return body;
    }

    @PostMapping("/answer")
    public Map<String, Object> saveAnswer(@RequestBody AnswerRequest request) {
        dictationService.saveAnswer(request.getClientId(), request.getIndex(), request.getAnswers());
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("ok", true);
        return body;
    }

    @GetMapping("/answer")
    public List<String> loadAnswer(@RequestParam("clientId") String clientId,
                                   @RequestParam("index") int index) {
        String answer = dictationService.loadAnswer(clientId, index);
        List<String> result = new ArrayList<String>();
        if (answer != null && !answer.isEmpty()) {
            String[] parts = answer.split("\\s+");
            for (String p : parts) {
                result.add(p);
            }
        }
        return result;
    }

    @GetMapping("/examples")
    public List<ExamplePair> examples(@RequestParam("clientId") String clientId,
                                      @RequestParam("index") int index) {
        return dictationService.examplesForWord(clientId, index);
    }

    @GetMapping("/speak")
    public ResponseEntity<byte[]> speak(@RequestParam("text") String text) {
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] audio = exampleService.ttsAudioBytes(text.trim());
        if (audio == null || audio.length == 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audio.length);
        return new ResponseEntity<byte[]>(audio, headers, HttpStatus.OK);
    }

    /**
     * 单词补全：根据 word 字段获取 phonetic 和 chinese，并更新内存中的词库数据
     */
    @PostMapping("/complete-word")
    public ResponseEntity<Map<String, Object>> completeWord(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        String id = body.get("id");
        if (word == null || word.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<String, Object>();
            err.put("ok", false);
            err.put("message", "word 不能为空");
            return ResponseEntity.badRequest().body(err);
        }

        String phonetic = null;
        String chinese = null;
        boolean needUpdate = false;

        // 查找当前单词信息，判断是否需要补全
        WordItem currentItem = null;
        if (id != null && !id.isEmpty()) {
            currentItem = dictationService.findWordById(id);
        }

        // 如果 phonetic 和 chinese 都已有值，直接返回，不调用 API
        if (currentItem != null 
                && currentItem.getPhonetic() != null && !currentItem.getPhonetic().isEmpty()
                && currentItem.getChinese() != null && !currentItem.getChinese().isEmpty()) {
            phonetic = currentItem.getPhonetic();
            chinese = currentItem.getChinese();
        } else {
            // 需要补全
            if (currentItem == null || currentItem.getPhonetic() == null || currentItem.getPhonetic().isEmpty()) {
                phonetic = exampleService.fetchPhonetic(word);
                needUpdate = true;
            } else {
                phonetic = currentItem.getPhonetic();
            }
            
            if (currentItem == null || currentItem.getChinese() == null || currentItem.getChinese().isEmpty()) {
                chinese = exampleService.fetchChineseMeaning(word);
                needUpdate = true;
            } else {
                chinese = currentItem.getChinese();
            }
        }

        // 更新内存中的词库
        if (needUpdate && id != null && !id.isEmpty()) {
            dictationService.updateWordItem(id, phonetic, chinese);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("id", id);
        result.put("word", word);
        result.put("phonetic", phonetic != null ? phonetic : "");
        result.put("chinese", chinese != null ? chinese : "");
        result.put("skipped", !needUpdate); // 标记是否跳过了 API 调用
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> wrapWords(String key, List<WordItem> words) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put(key, words);
        body.put("total", words.size());
        return body;
    }
}
