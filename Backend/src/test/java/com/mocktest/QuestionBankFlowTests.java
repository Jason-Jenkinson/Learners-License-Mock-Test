package com.mocktest;

import com.mocktest.dto.Dto;
import com.mocktest.model.AnswerOption;
import com.mocktest.repo.AnswerOptionRepository;
import com.mocktest.repo.CategoryRepository;
import com.mocktest.repo.QuestionRepository;
import com.mocktest.service.TestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the question bank and the full test flow, run against
 * the real Spring context with the "test" profile.
 *
 * Uses the real repositories and TestService to check
 * that the question bank is imported and that a complete test can be started,
 * answered and submitted.
 */
@SpringBootTest
@ActiveProfiles("test")
class QuestionBankFlowTests {
    @Autowired QuestionRepository questions;
    @Autowired AnswerOptionRepository options;
    @Autowired CategoryRepository categories;
    @Autowired TestService tests;

    /**
     * Checks that the JSON question bank was imported (more than 1000
     * questions) and that every category has at least one eligible question.
     */
    @Test
    void bankIsImportedIntoAllThreeCategories() {
        assertTrue(questions.count() > 1000, "expected the JSON bank to be imported");
        categories.findAll().forEach(c ->
                assertFalse(questions.findEligible(c.categoryId).isEmpty(), "no questions for " + c.categoryName));
    }

    /**
     * Checks that a full test contains 64 questions split across the
     * categories as 28 "Rules of the road", 28 "Road signs" and 8 "Controls of
     * the vehicle". Then answers every question correctly and checks that
     * submitting gives a score of 64, a pass, and no incorrect questions.
     */
    @Test
    void fullTestHas64QuestionsAndAllCorrectAnswersPass() {
        Dto.TestView view = tests.start(null);
        assertEquals(64, view.questions().size());

        Map<String, Long> perCategory = view.questions().stream()
                .collect(Collectors.groupingBy(q -> questions.findById(q.questionId()).orElseThrow().category.categoryName,
                        Collectors.counting()));
        assertEquals(Map.of("Rules of the road", 28L, "Road signs", 28L, "Controls of the vehicle", 8L), perCategory);

        for (Dto.QuestionView q : view.questions()) {
            AnswerOption right = options.findByQuestion_QuestionId(q.questionId()).stream()
                    .filter(o -> o.correct).findFirst().orElseThrow();
            tests.answer(view.attemptId(), new Dto.AnswerRequest(q.questionId(), right.optionId));
        }
        Dto.Result result = tests.submit(view.attemptId());
        assertEquals(64, result.score());
        assertTrue(result.passed());
        assertTrue(result.incorrect().isEmpty());
    }

    /**
     * Checks that submitting a test without answering anything gives a score
     * of 0, a fail, and lists all 64 questions as incorrect.
     */
    @Test
    void unansweredTestFailsAndListsEveryQuestion() {
        Dto.TestView view = tests.start(null);
        Dto.Result result = tests.submit(view.attemptId());
        assertEquals(0, result.score());
        assertFalse(result.passed());
        assertEquals(64, result.incorrect().size());
    }
}