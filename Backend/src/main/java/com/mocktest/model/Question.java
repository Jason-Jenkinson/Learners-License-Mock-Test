package com.mocktest.model;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long questionId;
    @ManyToOne(optional = false)
    public Category category;
    @Column(nullable = false, length = 1000)
    public String questionText;
    public String imagePath;
    @Column(length = 1000)
    public String explanation;
    @Column(nullable = false)
    public String source;
    @Column(nullable = false)
    public String verificationStatus = "Unverified"; // Unverified | Verified | Rejected
    @Column(name = "is_active", nullable = false)
    public boolean active = true;
}