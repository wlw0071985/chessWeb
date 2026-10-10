package com.chessweb.dictation;

import com.chessweb.dictation.dto.ClientRequest;
import com.chessweb.dictation.dto.SelectionRequest;
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

    private static Map<String, Object> wrapWords(String key, List<WordItem> words) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put(key, words);
        body.put("total", words.size());
        return body;
    }
}
