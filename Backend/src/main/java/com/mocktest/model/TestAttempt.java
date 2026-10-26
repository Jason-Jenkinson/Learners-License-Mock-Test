package com.mocktest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_attempts")
public class TestAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long attemptId;
    @ManyToOne(optional = true) // null = guest
    public User user;
    @Column(nullable = false)
    public LocalDateTime startedAt = LocalDateTime.now();
    public LocalDateTime submittedAt;
    @Column(name = "is_completed", nullable = false)
    public boolean completed = false;
    public Integer score;
    public Boolean passed;
}