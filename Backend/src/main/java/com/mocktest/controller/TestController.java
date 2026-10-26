package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.Category;
import com.mocktest.model.TestAttempt;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.service.TestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class TestController {
    private final TestService service;
    private final CategoryRepository categories;

    public TestController(TestService service, CategoryRepository categories) {
        this.service = service; this.categories = categories;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> guestAttempts(HttpSession s) {
        Set<Long> g = (Set<Long>) s.getAttribute("guestAttempts");
        if (g == null) { g = new HashSet<>(); s.setAttribute("guestAttempts", g); }
        return g;
    }

    /** Registered attempts belong to their user; guest attempts belong to the session that started them. */
    private void authorize(Long attemptId, HttpSession s) {
        TestAttempt a = service.getAttempt(attemptId);
        Long uid = (Long) s.getAttribute("userId");
        boolean ok = a.user != null ? a.user.userId.equals(uid) : guestAttempts(s).contains(attemptId);
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your attempt");
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return categories.findAll().stream()
                .map((Category c) -> Map.<String, Object>of("categoryId", c.categoryId, "name", c.categoryName))
                .toList();
    }

    @PostMapping("/tests")
    public Dto.TestView start(HttpSession s) {
        Long uid = (Long) s.getAttribute("userId");
        Dto.TestView view = service.start(uid);
        if (uid == null) guestAttempts(s).add(view.attemptId());
        return view;
    }

    @PutMapping("/tests/{id}/answers")
    public Map<String, String> answer(@PathVariable Long id, @RequestBody Dto.AnswerRequest r, HttpSession s) {
        authorize(id, s);
        service.answer(id, r);
        return Map.of("status", "saved");
    }

    @PostMapping("/tests/{id}/submit")
    public Dto.Result submit(@PathVariable Long id, HttpSession s) {
        authorize(id, s);
        return service.submit(id);
    }

    @GetMapping("/tests/{id}/results")
    public Dto.Result results(@PathVariable Long id, HttpSession s) {
        authorize(id, s);
        return service.results(id);
    }

    @GetMapping("/history")
    public List<Dto.AttemptSummary> history(HttpSession s) {
        Long uid = (Long) s.getAttribute("userId");
        if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in to see your history");
        return service.history(uid);
    }
}