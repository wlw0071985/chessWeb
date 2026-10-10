package com.chessweb.dictation;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class DictationExceptionHandler {

    @ExceptionHandler(DictationException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(DictationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<Map<String, String>> handleRedis(Exception ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "无法连接 Redis（127.0.0.1:6379），请确认本地 Redis 已启动");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
