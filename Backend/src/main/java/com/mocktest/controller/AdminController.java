package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.service.QuestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/questions")
public class AdminController {
    private final QuestionService service;
    private final String adminKey;

    public AdminController(QuestionService service, @Value("${app.admin-key}") String adminKey) {
        this.service = service; this.adminKey = adminKey;
    }

    private void check(String key) {
        if (key == null || !key.equals(adminKey))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin key required (X-Admin-Key header)");
    }

    @GetMapping
    public List<Dto.AdminQuestion> list(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        check(key);
        return service.list();
    }

    @PostMapping
    public Map<String, Object> add(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                   @RequestBody Dto.QuestionRequest r) {
        check(key);
        return Map.of("questionId", service.create(r, null).questionId, "status", "Unverified");
    }

    @PostMapping("/{id}/verify")
    public Map<String, Object> verify(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                      @PathVariable Long id, @RequestParam(defaultValue = "Verified") String status) {
        check(key);
        return Map.of("questionId", id, "status", service.setStatus(id, status).verificationStatus);
    }

    @PostMapping("/{id}/retire")
    public Map<String, Object> retire(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                      @PathVariable Long id) {
        check(key);
        service.retire(id);
        return Map.of("questionId", id, "active", false);
    }

    @PostMapping("/{id}/correct")
    public Map<String, Object> correct(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                       @PathVariable Long id, @RequestBody Dto.QuestionRequest r) {
        check(key);
        return Map.of("retiredQuestionId", id, "newQuestionId", service.correct(id, r).questionId, "status", "Unverified");
    }
}