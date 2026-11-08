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

@SpringBootTest
@ActiveProfiles("test")
class QuestionBankFlowTests {
    @Autowired QuestionRepository questions;
    @Autowired AnswerOptionRepository options;
    @Autowired CategoryRepository categories;
    @Autowired TestService tests;

    @Test
    void bankIsImportedIntoAllThreeCategories() {
        assertTrue(questions.count() > 1000, "expected the JSON bank to be imported");
        categories.findAll().forEach(c ->
                assertFalse(questions.findEligible(c.categoryId).isEmpty(), "no questions for " + c.categoryName));
    }

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

    @Test
    void unansweredTestFailsAndListsEveryQuestion() {
        Dto.TestView view = tests.start(null);
        Dto.Result result = tests.submit(view.attemptId());
        assertEquals(0, result.score());
        assertFalse(result.passed());
        assertEquals(64, result.incorrect().size());
    }
}
