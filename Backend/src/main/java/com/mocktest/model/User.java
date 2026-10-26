package com.mocktest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long userId;
    @Column(unique = true, nullable = false)
    public String username;
    @Column(nullable = false)
    public String passwordHash;
    @Column(nullable = false)
    public LocalDateTime dateRegistered = LocalDateTime.now();
}