package com.mocktest.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attempt_questions")
public class AttemptQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long attemptQuestionId;
    @ManyToOne(optional = false)
    public TestAttempt attempt;
    @ManyToOne(optional = false)
    public Question question;
    @Column(nullable = false)
    public int questionOrder;
    @ManyToOne(optional = true) // null = skipped
    public AnswerOption selectedOption;
}