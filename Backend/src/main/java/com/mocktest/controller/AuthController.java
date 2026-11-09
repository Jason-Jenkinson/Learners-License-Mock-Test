package com.mocktest.controller;

import com.mocktest.dto.Dto;
import com.mocktest.model.User;
import com.mocktest.repo.UserRepository;
import com.mocktest.service.PasswordHasher;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;

    public AuthController(UserRepository users) { this.users = users; }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Dto.Credentials c, HttpSession s) {
        if (c.username() == null || c.username().trim().length() < 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at least 3 characters");
        if (c.password() == null || c.password().length() < 6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        if (users.findByUsername(c.username().trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        User u = new User();
        u.username = c.username().trim();
        u.passwordHash = PasswordHasher.hash(c.password());
        u = users.save(u);
        s.setAttribute("userId", u.userId);
        return Map.of("userId", u.userId, "username", u.username);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Dto.Credentials c, HttpSession s) {
        User u = users.findByUsername(c.username() == null ? "" : c.username().trim())
                .filter(x -> PasswordHasher.verify(c.password() == null ? "" : c.password(), x.passwordHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        s.setAttribute("userId", u.userId);
        return Map.of("userId", u.userId, "username", u.username);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession s) {
        s.invalidate();
        return Map.of("status", "logged out");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession s) {
        Long id = (Long) s.getAttribute("userId");
        if (id == null) return Map.of("guest", true);
        return users.findById(id).map(u -> Map.<String, Object>of("userId", u.userId, "username", u.username))
                .orElse(Map.of("guest", true));
    }
}