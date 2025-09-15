package com.example.helloworld.calendar_diary_server.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매우 단순한 헬스 체크 컨트롤러.
 * - GET /           -> 200 OK, "ok"
 * - GET /health     -> 200 OK, "ok"
 * - GET /ping       -> 200 OK, "pong"
 *
 * 프로브를 / 로 설정해두면 이 컨트롤러가 200을 반환해서 준비/생존 체크가 통과됩니다.
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}