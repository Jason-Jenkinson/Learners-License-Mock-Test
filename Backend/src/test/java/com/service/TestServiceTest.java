package com.mocktest.service;

import com.mocktest.TestData;
import com.mocktest.dto.Dto;
import com.mocktest.model.*;
import com.mocktest.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mocktest.TestData.assertStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestServiceTest {
    private TestAttemptRepository attempts;
    private AttemptQuestionRepository aqs;
    private QuestionRepository questions;
    private AnswerOptionRepository options;
    private CategoryRepository categories;
    private UserRepository users;
    private TestService service;

    // Category names must match the RULES map in TestService.
    private Category controls;   // 8 questions, pass mark 6
    private Category roadSigns;  // 28 questions, pass mark 23

    @BeforeEach
    void setUp() {
        attempts = mock(TestAttemptRepository.class);
        aqs = mock(AttemptQuestionRepository.class);
        questions = mock(QuestionRepository.class);
        options = mock(AnswerOptionRepository.class);
        categories = mock(CategoryRepository.class);
        users = mock(UserRepository.class);
        service = new TestService(attempts, aqs, questions, options, categories, users);

        controls = TestData.category(3, "Controls of the vehicle");
        roadSigns = TestData.category(2, "Road signs");
    }

    // ---------- helpers ----------

    private TestAttempt attempt(long id) {
        TestAttempt a = TestData.attempt(id, null);
        when(attempts.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    /** answer: TRUE = correct option picked, FALSE = wrong option picked, null = skipped. */
    private AttemptQuestion row(TestAttempt a, Category cat, long qid, int order, Boolean answer) {
        Question q = TestData.question(qid, cat);
        AnswerOption right = TestData.option(qid * 10 + 1, q, true);
        AnswerOption wrong = TestData.option(qid * 10 + 2, q, false);
        when(options.findByQuestion_QuestionId(qid)).thenReturn(List.of(right, wrong));
        AnswerOption picked = answer == null ? null : (answer ? right : wrong);
        return TestData.attemptQuestion(a, q, order, picked);
    }

    private void stubRows(TestAttempt a, AttemptQuestion... rows) {
        when(aqs.findByAttempt_AttemptIdOrderByQuestionOrder(a.attemptId)).thenReturn(List.of(rows));
    }

    private AttemptQuestion existingRow(TestAttempt a, long qid) {
        Question q = TestData.question(qid, controls);
        AttemptQuestion aq = TestData.attemptQuestion(a, q, 1, null);
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(a.attemptId, qid)).thenReturn(Optional.of(aq));
        return aq;
    }

    // ---------- getAttempt ----------

    @Test
    void getAttempt_unknownId_isNotFound() {
        assertStatus(404, () -> service.getAttempt(42L));
    }

    // ---------- submit and scoring ----------

    @Test
    void submit_allCorrect_passesAndStoresScore() {
        TestAttempt a = attempt(1L);
        stubRows(a,
                row(a, controls, 1, 1, true), row(a, controls, 2, 2, true),
                row(a, controls, 3, 3, true), row(a, controls, 4, 4, true));

        Dto.Result r = service.submit(1L);

        assertEquals(4, r.score());
        assertEquals(4, r.total());
        assertTrue(r.passed());
        assertTrue(r.incorrect().isEmpty());
        assertTrue(a.completed);
        assertNotNull(a.submittedAt);
        assertEquals(4, a.score);
        assertTrue(a.passed);
        verify(attempts).save(a);
    }

    @Test
    void submit_passMarkScalesWithQuestionsAsked_threeOfFourPasses() {
        // 6 of 8 required, so 4 asked needs ceil(6 * 4 / 8) = 3
        TestAttempt a = attempt(1L);
        stubRows(a,
                row(a, controls, 1, 1, true), row(a, controls, 2, 2, true),
                row(a, controls, 3, 3, true), row(a, controls, 4, 4, false));

        Dto.Result r = service.submit(1L);

        assertTrue(r.passed());
        assertEquals(3, r.categories().get(0).required());
        assertEquals(3, r.categories().get(0).correct());
        assertEquals(4, r.categories().get(0).asked());
    }

    @Test
    void submit_belowScaledPassMark_fails() {
        TestAttempt a = attempt(1L);
        stubRows(a,
                row(a, controls, 1, 1, true), row(a, controls, 2, 2, true),
                row(a, controls, 3, 3, false), row(a, controls, 4, 4, false));

        Dto.Result r = service.submit(1L);

        assertFalse(r.passed());
        assertFalse(r.categories().get(0).passed());
        assertEquals(2, r.score());
    }

    @Test
    void submit_skippedQuestionCountsAsIncorrect() {
        TestAttempt a = attempt(1L);
        stubRows(a, row(a, controls, 1, 1, true), row(a, controls, 2, 2, null));

        Dto.Result r = service.submit(1L);

        assertEquals(1, r.score());
        assertEquals(1, r.incorrect().size());
        Dto.IncorrectQuestion missed = r.incorrect().get(0);
        assertEquals(2L, missed.questionId());
        assertNull(missed.selectedOptionId());
        assertNull(missed.selectedText());
        assertEquals(21L, missed.correctOptionId());
    }

    @Test
    void submit_wrongAnswerReportsSelectedAndCorrectOption() {
        TestAttempt a = attempt(1L);
        stubRows(a, row(a, controls, 1, 1, false));

        Dto.Result r = service.submit(1L);

        Dto.IncorrectQuestion wrong = r.incorrect().get(0);
        assertEquals(12L, wrong.selectedOptionId());
        assertEquals(11L, wrong.correctOptionId());
        assertEquals("Because", wrong.explanation());
    }

    @Test
    void submit_oneFailedCategoryFailsTheWholeTest() {
        TestAttempt a = attempt(1L);
        stubRows(a,
                row(a, controls, 1, 1, true), row(a, controls, 2, 2, true),
                row(a, controls, 3, 3, true), row(a, controls, 4, 4, true),
                row(a, roadSigns, 5, 5, false));

        Dto.Result r = service.submit(1L);

        assertEquals(2, r.categories().size());
        assertFalse(r.passed());
        assertEquals(4, r.score());
        assertEquals(5, r.total());
    }

    @Test
    void submit_alreadySubmitted_isConflict() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(409, () -> service.submit(1L));
        verify(attempts, never()).save(any());
    }

    @Test
    void submit_unknownAttempt_isNotFound() {
        assertStatus(404, () -> service.submit(42L));
    }

    // ---------- answer ----------

    @Test
    void answer_afterSubmit_isConflict() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(409, () -> service.answer(1L, new Dto.AnswerRequest(1L, 1L)));
        verify(aqs, never()).save(any());
    }

    @Test
    void answer_questionNotInAttempt_isNotFound() {
        attempt(1L);

        assertStatus(404, () -> service.answer(1L, new Dto.AnswerRequest(99L, 1L)));
    }

    @Test
    void answer_unknownOption_isBadRequest() {
        TestAttempt a = attempt(1L);
        existingRow(a, 1L);

        assertStatus(400, () -> service.answer(1L, new Dto.AnswerRequest(1L, 999L)));
        verify(aqs, never()).save(any());
    }

    @Test
    void answer_optionFromAnotherQuestion_isBadRequest() {
        TestAttempt a = attempt(1L);
        existingRow(a, 1L);
        Question other = TestData.question(2, controls);
        when(options.findById(5L)).thenReturn(Optional.of(TestData.option(5, other, true)));

        assertStatus(400, () -> service.answer(1L, new Dto.AnswerRequest(1L, 5L)));
        verify(aqs, never()).save(any());
    }

    @Test
    void answer_validOption_isSaved() {
        TestAttempt a = attempt(1L);
        AttemptQuestion aq = existingRow(a, 1L);
        AnswerOption chosen = TestData.option(5, aq.question, true);
        when(options.findById(5L)).thenReturn(Optional.of(chosen));

        service.answer(1L, new Dto.AnswerRequest(1L, 5L));

        assertSame(chosen, aq.selectedOption);
        verify(aqs).save(aq);
    }

    @Test
    void answer_changingAnswer_replacesThePreviousOne() {
        TestAttempt a = attempt(1L);
        AttemptQuestion aq = existingRow(a, 1L);
        AnswerOption first = TestData.option(5, aq.question, false);
        AnswerOption second = TestData.option(6, aq.question, true);
        when(options.findById(5L)).thenReturn(Optional.of(first));
        when(options.findById(6L)).thenReturn(Optional.of(second));

        service.answer(1L, new Dto.AnswerRequest(1L, 5L));
        service.answer(1L, new Dto.AnswerRequest(1L, 6L));

        assertSame(second, aq.selectedOption);
    }

    @Test
    void answer_nullOption_clearsTheSelection() {
        TestAttempt a = attempt(1L);
        AttemptQuestion aq = existingRow(a, 1L);
        aq.selectedOption = TestData.option(5, aq.question, true);

        service.answer(1L, new Dto.AnswerRequest(1L, null));

        assertNull(aq.selectedOption);
        verify(aqs).save(aq);
    }

    // ---------- results ----------

    @Test
    void results_beforeSubmit_isConflict() {
        attempt(1L);

        assertStatus(409, () -> service.results(1L));
    }

    @Test
    void results_afterSubmit_usesStoredScoreAndPassFlag() {
        TestAttempt a = attempt(1L);
        a.completed = true;
        a.score = 3;
        a.passed = true;
        stubRows(a,
                row(a, controls, 1, 1, true), row(a, controls, 2, 2, true),
                row(a, controls, 3, 3, true), row(a, controls, 4, 4, false));

        Dto.Result r = service.results(1L);

        assertEquals(3, r.score());
        assertEquals(4, r.total());
        assertTrue(r.passed());
        assertEquals(1, r.incorrect().size());
    }

    // ---------- claim ----------

    @Test
    void claim_unfinishedTest_isConflict() {
        attempt(1L);

        assertStatus(409, () -> service.claim(1L, 3L));
    }

    @Test
    void claim_attemptOfAnotherUser_isForbidden() {
        TestAttempt a = attempt(1L);
        a.completed = true;
        a.user = TestData.user(9, "someone");

        assertStatus(403, () -> service.claim(1L, 3L));
        verify(attempts, never()).save(any());
    }

    @Test
    void claim_unknownUser_isNotFound() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(404, () -> service.claim(1L, 3L));
    }

    @Test
    void claim_guestAttempt_isLinkedToTheUser() {
        TestAttempt a = attempt(1L);
        a.completed = true;
        User u = TestData.user(3, "ching");
        when(users.findById(3L)).thenReturn(Optional.of(u));

        service.claim(1L, 3L);

        assertSame(u, a.user);
        verify(attempts).save(a);
    }

    // ---------- history ----------

    @Test
    void history_mapsAttemptsToSummaries() {
        TestAttempt a1 = TestData.attempt(1, null);
        TestAttempt a2 = TestData.attempt(2, null);
        a2.completed = true;
        a2.score = 20;
        a2.passed = true;
        when(attempts.findByUser_UserIdOrderByStartedAtDesc(3L)).thenReturn(List.of(a2, a1));

        List<Dto.AttemptSummary> result = service.history(3L);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).attemptId());
        assertTrue(result.get(0).completed());
        assertEquals(20, result.get(0).score());
        assertFalse(result.get(1).completed());
    }

    // ---------- start ----------

    private void stubAttemptSave(long id) {
        when(attempts.save(any(TestAttempt.class))).thenAnswer(i -> {
            TestAttempt t = i.getArgument(0);
            t.attemptId = id;
            return t;
        });
    }

    @Test
    void start_noEligibleQuestions_isConflict() {
        stubAttemptSave(7L);
        when(categories.findAll()).thenReturn(List.of(controls));
        when(questions.findEligible(3L)).thenReturn(List.of());

        assertStatus(409, () -> service.start(null));
        verify(aqs, never()).save(any());
    }

    @Test
    void start_returnsQuestionsWithOptionsAndNumbersThem() {
        stubAttemptSave(7L);
        when(categories.findAll()).thenReturn(List.of(controls));
        Question q1 = TestData.question(1, controls);
        Question q2 = TestData.question(2, controls);
        when(questions.findEligible(3L)).thenReturn(List.of(q1, q2));
        when(options.findByQuestion_QuestionId(1L)).thenReturn(
                List.of(TestData.option(11, q1, true), TestData.option(12, q1, false)));
        when(options.findByQuestion_QuestionId(2L)).thenReturn(
                List.of(TestData.option(21, q2, true), TestData.option(22, q2, false)));

        Dto.TestView view = service.start(null);

        assertEquals(7L, view.attemptId());
        assertEquals(2, view.questions().size());
        List<Integer> orders = new ArrayList<>();
        for (Dto.QuestionView qv : view.questions()) {
            assertEquals(2, qv.options().size());
            orders.add(qv.order());
        }
        orders.sort(null);
        assertEquals(List.of(1, 2), orders);
        verify(aqs, times(2)).save(any(AttemptQuestion.class));
    }

    @Test
    void start_viewDoesNotRevealWhichOptionIsCorrect() {
        // OptionView only carries id and text, so the correct flag cannot leak to the browser.
        assertEquals(2, Dto.OptionView.class.getRecordComponents().length);
    }

    @Test
    void start_registeredUser_isLinkedToTheAttempt() {
        stubAttemptSave(7L);
        User u = TestData.user(3, "ching");
        when(users.findById(3L)).thenReturn(Optional.of(u));
        when(categories.findAll()).thenReturn(List.of(controls));
        Question q1 = TestData.question(1, controls);
        when(questions.findEligible(3L)).thenReturn(List.of(q1));
        when(options.findByQuestion_QuestionId(1L)).thenReturn(
                List.of(TestData.option(11, q1, true), TestData.option(12, q1, false)));

        service.start(3L);

        ArgumentCaptor<TestAttempt> captor = ArgumentCaptor.forClass(TestAttempt.class);
        verify(attempts).save(captor.capture());
        assertSame(u, captor.getValue().user);
    }
}
