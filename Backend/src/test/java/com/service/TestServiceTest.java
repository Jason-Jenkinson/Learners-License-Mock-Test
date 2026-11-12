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

/**
 * Unit tests for {@link TestService}, using mocked repositories.
 * Covers starting an attempt, saving answers, submitting and scoring,
 * retrieving results, claiming a guest attempt for a user, and listing a
 * user's history. Error cases check the HTTP status the service reports
 * (404 not found, 409 conflict, 400 bad request, 403 forbidden).
 */
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

    /**
     * Creates fresh mock repositories, builds the {@link TestService} under
     * test, and prepares the two categories used by the tests.
     */
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

    /**
     * Creates a guest attempt with the given id and makes the attempt
     * repository return it from {@code findById}.
     *
     * @param id the attempt id
     * @return the stubbed attempt
     */
    private TestAttempt attempt(long id) {
        TestAttempt a = TestData.attempt(id, null);
        when(attempts.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    /**
     * Builds an attempt question with one correct and one wrong option, and
     * stubs the option repository to return both.
     *
     * @param a      the attempt the row belongs to
     * @param cat    the category of the question
     * @param qid    the question id (option ids are {@code qid * 10 + 1} for
     *               the correct option and {@code qid * 10 + 2} for the wrong one)
     * @param order  the position of the question in the attempt
     * @param answer TRUE = correct option picked, FALSE = wrong option picked,
     *               null = skipped
     * @return the attempt question row
     */
    private AttemptQuestion row(TestAttempt a, Category cat, long qid, int order, Boolean answer) {
        Question q = TestData.question(qid, cat);
        AnswerOption right = TestData.option(qid * 10 + 1, q, true);
        AnswerOption wrong = TestData.option(qid * 10 + 2, q, false);
        when(options.findByQuestion_QuestionId(qid)).thenReturn(List.of(right, wrong));
        AnswerOption picked = answer == null ? null : (answer ? right : wrong);
        return TestData.attemptQuestion(a, q, order, picked);
    }

    /**
     * Makes the attempt question repository return the given rows, in order,
     * for the given attempt.
     * @param a is the attempt
     * @param the rows to return
     */
    private void stubRows(TestAttempt a, AttemptQuestion... rows) {
        when(aqs.findByAttempt_AttemptIdOrderByQuestionOrder(a.attemptId)).thenReturn(List.of(rows));
    }

    /**
     * Creates an unanswered attempt question for the given question id and
     * stubs the repository lookup by attempt and question.
     * @param a   the attempt
     * @param qid the question id
     * @return the stubbed attempt question
     */
    private AttemptQuestion existingRow(TestAttempt a, long qid) {
        Question q = TestData.question(qid, controls);
        AttemptQuestion aq = TestData.attemptQuestion(a, q, 1, null);
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(a.attemptId, qid)).thenReturn(Optional.of(aq));
        return aq;
    }

    /**
     * Checks that getAttempt responds with 404 when no attempt exists
     * for the given id.
     */
    @Test
    void getAttempt_unknownId_isNotFound() {
        assertStatus(404, () -> service.getAttempt(42L));
    }

    /**
     * Checks that submitting an attempt with every answer correct passes,
     * returns no incorrect questions, and stores the score, pass flag,
     * completed flag and submission time on the attempt before saving it.
     */
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

    /**
     * Checks that the category pass mark is scaled to the number of questions
     * actually asked. With 4 of 8 questions asked, the required score is
     * ceil(6 * 4 / 8) = 3, so 3 correct answers pass.
     */
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

    /**
     * Checks that a category with fewer correct answers than the scaled pass
     * mark (2 of 4, where 3 are needed) fails, and so does the whole result.
     */
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

    /**
     * Checks that a skipped question is scored as incorrect and reported with
     * no selected option or text, but with the id of the correct option.
     */
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

    /**
     * Checks that a wrongly answered question is reported with the id of the
     * option the user selected, the id of the correct option, and the
     * question's explanation.
     */
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

    /**
     * Checks that failing one category fails the whole test, even when
     * another category is answered perfectly. The result must list both
     * categories and count all questions in the total.
     */
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

    /**
     * Checks that submitting an attempt that is already completed responds
     * with 409 and does not save anything.
     */
    @Test
    void submit_alreadySubmitted_isConflict() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(409, () -> service.submit(1L));
        verify(attempts, never()).save(any());
    }

    /**
     * Checks that submitting an attempt that does not exist responds with 404.
     */
    @Test
    void submit_unknownAttempt_isNotFound() {
        assertStatus(404, () -> service.submit(42L));
    }

    /**
     * Checks that answering a question after the attempt has been submitted
     * responds with 409 and does not save the answer.
     */
    @Test
    void answer_afterSubmit_isConflict() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(409, () -> service.answer(1L, new Dto.AnswerRequest(1L, 1L)));
        verify(aqs, never()).save(any());
    }

    /**
     * Checks that answering a question that is not part of the attempt
     * responds with 404.
     */
    @Test
    void answer_questionNotInAttempt_isNotFound() {
        attempt(1L);

        assertStatus(404, () -> service.answer(1L, new Dto.AnswerRequest(99L, 1L)));
    }

    /**
     * Checks that choosing an option id that does not exist responds with 400
     * and does not save anything.
     */
    @Test
    void answer_unknownOption_isBadRequest() {
        TestAttempt a = attempt(1L);
        existingRow(a, 1L);

        assertStatus(400, () -> service.answer(1L, new Dto.AnswerRequest(1L, 999L)));
        verify(aqs, never()).save(any());
    }

    /**
     * Checks that choosing an option that belongs to a different question
     * responds with 400 and does not save anything.
     */
    @Test
    void answer_optionFromAnotherQuestion_isBadRequest() {
        TestAttempt a = attempt(1L);
        existingRow(a, 1L);
        Question other = TestData.question(2, controls);
        when(options.findById(5L)).thenReturn(Optional.of(TestData.option(5, other, true)));

        assertStatus(400, () -> service.answer(1L, new Dto.AnswerRequest(1L, 5L)));
        verify(aqs, never()).save(any());
    }

    /**
     * Checks that a valid option belonging to the question is stored as the
     * selected option and the attempt question is saved.
     */
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

    /**
     * Checks that answering the same question a second time replaces the
     * earlier selection with the new one.
     */
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

    /**
     * Checks that sending a null option id clears the existing selection and
     * saves the attempt question.
     */
    @Test
    void answer_nullOption_clearsTheSelection() {
        TestAttempt a = attempt(1L);
        AttemptQuestion aq = existingRow(a, 1L);
        aq.selectedOption = TestData.option(5, aq.question, true);

        service.answer(1L, new Dto.AnswerRequest(1L, null));

        assertNull(aq.selectedOption);
        verify(aqs).save(aq);
    }

    /**
     * Checks that requesting results for an attempt that has not been
     * submitted responds with 409.
     */
    @Test
    void results_beforeSubmit_isConflict() {
        attempt(1L);

        assertStatus(409, () -> service.results(1L));
    }

    /**
     * Checks that results for a completed attempt use the score and pass flag
     * stored on the attempt, and still list the incorrect questions.
     */
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

    /**
     * Checks that claiming an attempt that has not been completed responds
     * with 409.
     */
    @Test
    void claim_unfinishedTest_isConflict() {
        attempt(1L);

        assertStatus(409, () -> service.claim(1L, 3L));
    }

    /**
     * Checks that claiming an attempt that already belongs to a different
     * user responds with 403 and does not save anything.
     */
    @Test
    void claim_attemptOfAnotherUser_isForbidden() {
        TestAttempt a = attempt(1L);
        a.completed = true;
        a.user = TestData.user(9, "someone");

        assertStatus(403, () -> service.claim(1L, 3L));
        verify(attempts, never()).save(any());
    }

    /**
     * Checks that claiming an attempt for a user id that does not exist
     * responds with 404.
     */
    @Test
    void claim_unknownUser_isNotFound() {
        TestAttempt a = attempt(1L);
        a.completed = true;

        assertStatus(404, () -> service.claim(1L, 3L));
    }

    /**
     * Checks that claiming a completed guest attempt links it to the user and
     * saves the attempt.
     */
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

    /**
     * Checks that history maps a user's attempts to summaries and
     * keeps the repository order (newest first), carrying over the id,
     * completed flag and score.
     */
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

    /**
     * Makes attempts.save assign the given id to the attempt it
     * receives, mimicking database id generation.
     *
     * @param id the id to assign
     */
    private void stubAttemptSave(long id) {
        when(attempts.save(any(TestAttempt.class))).thenAnswer(i -> {
            TestAttempt t = i.getArgument(0);
            t.attemptId = id;
            return t;
        });
    }

    /**
     * Checks that starting a test when no eligible questions exist responds
     * with 409 and saves no attempt questions.
     */
    @Test
    void start_noEligibleQuestions_isConflict() {
        stubAttemptSave(7L);
        when(categories.findAll()).thenReturn(List.of(controls));
        when(questions.findEligible(3L)).thenReturn(List.of());

        assertStatus(409, () -> service.start(null));
        verify(aqs, never()).save(any());
    }

    /**
     * Checks that starting a test returns the new attempt id and every
     * selected question with its options, numbers the questions 1 to n (in
     * any order), and saves one attempt question per question.
     */
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

    /**
     * Checks that the view sent to the browser cannot reveal the correct
     * answer. OptionView must only have two fields (id and text), so
     * the correct flag is never exposed.
     */
    @Test
    void start_viewDoesNotRevealWhichOptionIsCorrect() {
        // OptionView only carries id and text, so the correct flag cannot leak to the browser.
        assertEquals(2, Dto.OptionView.class.getRecordComponents().length);
    }

    /**
     * Checks that starting a test for a registered user looks the user up and
     * links that user to the saved attempt.
     */
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