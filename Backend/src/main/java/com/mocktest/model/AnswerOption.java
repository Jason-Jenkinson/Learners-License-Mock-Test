package com.mocktest.model;

import jakarta.persistence.*;

@Entity
@Table(name = "answer_options")
public class AnswerOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long optionId;
    @ManyToOne(optional = false)
    public Question question;
    @Column(nullable = false, length = 500)
    public String optionText;
    @Column(name = "is_correct", nullable = false)
    public boolean correct;
}