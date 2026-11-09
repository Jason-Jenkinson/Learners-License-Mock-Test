package com.mocktest.service;

import com.mocktest.dto.Dto;
import com.mocktest.model.*;
import com.mocktest.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {
    @Mock TestAttemptRepository attempts;
    @Mock AttemptQuestionRepository aqs;
    @Mock QuestionRepository questions;
    @Mock AnswerOptionRepository options;
    @Mock CategoryRepository categories;
    @Mock UserRepository users;

    TestService service;
    long nextId = 1000;

    @BeforeEach
    void setUp() {
        service = new TestService(attempts, aqs, questions, options, categories, users);
    }

    // ---------- helpers ----------

    private static HttpStatusCode statusOf(Executable e) {
        return assertThrows(ResponseStatusException.class, e).getStatusCode();
    }

    private static Category category(long id, String name) {
        Category c = new Category();
        c.categoryId = id;
        c.categoryName = name;
        return c;
    }

    private Question question(Category c) {
        Question q = new Question();
        q.questionId = nextId++;
        q.category = c;
        q.questionText = "Question " + q.questionId;
        q.explanation = "Explanation " + q.questionId;
        q.verificationStatus = "Verified";
        return q;
    }

    private AnswerOption option(Question q, String text, boolean correct) {
        AnswerOption o = new AnswerOption();
        o.optionId = nextId++;
        o.question = q;
        o.optionText = text;
        o.correct = correct;
        return o;
    }

    private static User user(long id) {
        User u = new User();
        u.userId = id;
        u.username = "user" + id;
        return u;
    }

    private static TestAttempt attempt(long id, User u, boolean completed) {
        TestAttempt a = new TestAttempt();
        a.attemptId = id;
        a.user = u;
        a.completed = completed;
        return a;
    }

    private static AttemptQuestion row(TestAttempt a, Question q, int order, AnswerOption selected) {
        AttemptQuestion aq = new AttemptQuestion();
        aq.attempt = a;
        aq.question = q;
        aq.questionOrder = order;
        aq.selectedOption = selected;
        return aq;
    }

    /** One question per entry: true = answered correctly, false = answered wrongly, null = skipped. */
    private List<AttemptQuestion> rows(TestAttempt a, Category c, Boolean... answers) {
        List<AttemptQuestion> rows = new ArrayList<>();
        for (Boolean ans : answers) {
            Question q = question(c);
            AnswerOption right = option(q, "right", true);
            AnswerOption wrong = option(q, "wrong", false);
            lenient().when(options.findByQuestion_QuestionId(q.questionId)).thenReturn(List.of(wrong, right));
            rows.add(row(a, q, rows.size() + 1, ans == null ? null : ans ? right : wrong));
        }
        return rows;
    }

    private static Boolean[] answers(int correct, int wrong) {
        Boolean[] a = new Boolean[correct + wrong];
        Arrays.fill(a, 0, correct, true);
        Arrays.fill(a, correct, a.length, false);
        return a;
    }

    // ---------- start ----------

    @Test
    void startPicksRuleCountPerCategoryAndUsesDefaultForUnknownCategories() {
        Category rules = category(1, "Rules of the road");
        Category extra = category(9, "Something new");
        when(attempts.save(any(TestAttempt.class))).thenAnswer(inv -> {
            TestAttempt a = inv.getArgument(0);
            a.attemptId = 5L;
            return a;
        });
        when(categories.findAll()).thenReturn(List.of(rules, extra));
        List<Question> rulesPool = IntStream.range(0, 40).mapToObj(i -> question(rules)).toList();
        List<Question> extraPool = IntStream.range(0, 15).mapToObj(i -> question(extra)).toList();
        when(questions.findEligible(1L)).thenReturn(rulesPool);
        when(questions.findEligible(9L)).thenReturn(extraPool);
        when(options.findByQuestion_QuestionId(anyLong())).thenAnswer(inv -> {
            Question q = new Question();
            q.questionId = inv.getArgument(0);
            return List.of(option(q, "A", true), option(q, "B", false));
        });

        Dto.TestView view = service.start(null);

        assertEquals(5L, view.attemptId());
        assertEquals(28 + 10, view.questions().size()); // Rules rule = 28, default rule = 10
        long rulesCount = view.questions().stream()
                .filter(v -> rulesPool.stream().anyMatch(q -> q.questionId.equals(v.questionId()))).count();
        assertEquals(28, rulesCount);
        assertEquals(view.questions().size(), view.questions().stream().map(Dto.QuestionView::questionId).distinct().count());
        assertEquals(IntStream.rangeClosed(1, 38).boxed().toList(),
                view.questions().stream().map(Dto.QuestionView::order).toList());
        view.questions().forEach(v -> assertEquals(2, v.options().size()));
        verify(aqs, times(38)).save(any(AttemptQuestion.class));
        verifyNoInteractions(users);
    }

    @Test
    void startTakesWholePoolWhenSmallerThanRule() {
        Category signs = category(2, "Road signs");
        when(attempts.save(any(TestAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categories.findAll()).thenReturn(List.of(signs));
        when(questions.findEligible(2L)).thenReturn(List.of(question(signs), question(signs), question(signs)));
        when(options.findByQuestion_QuestionId(anyLong())).thenReturn(List.of());

        assertEquals(3, service.start(null).questions().size());
    }

    @Test
    void startLinksAttemptToLoggedInUser() {
        Category signs = category(2, "Road signs");
        User u = user(3);
        when(users.findById(3L)).thenReturn(Optional.of(u));
        ArgumentCaptor<TestAttempt> saved = ArgumentCaptor.forClass(TestAttempt.class);
        when(attempts.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(categories.findAll()).thenReturn(List.of(signs));
        when(questions.findEligible(2L)).thenReturn(List.of(question(signs)));
        when(options.findByQuestion_QuestionId(anyLong())).thenReturn(List.of());

        service.start(3L);

        assertSame(u, saved.getValue().user);
    }

    @Test
    void startWithNoEligibleQuestionsIsConflict() {
        when(attempts.save(any(TestAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categories.findAll()).thenReturn(List.of(category(1, "Rules of the road")));
        when(questions.findEligible(1L)).thenReturn(List.of());

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.start(null)));
    }

    // ---------- answer ----------

    @Test
    void answerUnknownAttemptIs404() {
        when(attempts.findById(1L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.answer(1L, new Dto.AnswerRequest(2L, 3L))));
    }

    @Test
    void answerAfterSubmitIsConflict() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, true)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.answer(1L, new Dto.AnswerRequest(2L, 3L))));
    }

    @Test
    void answerForQuestionNotInAttemptIs404() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, false)));
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(1L, 2L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.answer(1L, new Dto.AnswerRequest(2L, 3L))));
    }

    @Test
    void answerWithUnknownOptionIsBadRequest() {
        TestAttempt a = attempt(1, null, false);
        Question q = question(category(1, "Road signs"));
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(1L, q.questionId)).thenReturn(Optional.of(row(a, q, 1, null)));
        when(options.findById(77L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.answer(1L, new Dto.AnswerRequest(q.questionId, 77L))));
    }

    @Test
    void answerWithOptionFromAnotherQuestionIsBadRequest() {
        Category c = category(1, "Road signs");
        TestAttempt a = attempt(1, null, false);
        Question q = question(c);
        AnswerOption foreign = option(question(c), "other", true);
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(1L, q.questionId)).thenReturn(Optional.of(row(a, q, 1, null)));
        when(options.findById(foreign.optionId)).thenReturn(Optional.of(foreign));

        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> service.answer(1L, new Dto.AnswerRequest(q.questionId, foreign.optionId))));
        verify(aqs, never()).save(any());
    }

    @Test
    void answerSavesSelectedOption() {
        TestAttempt a = attempt(1, null, false);
        Question q = question(category(1, "Road signs"));
        AnswerOption o = option(q, "Stop", true);
        AttemptQuestion aq = row(a, q, 1, null);
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(1L, q.questionId)).thenReturn(Optional.of(aq));
        when(options.findById(o.optionId)).thenReturn(Optional.of(o));

        service.answer(1L, new Dto.AnswerRequest(q.questionId, o.optionId));

        assertSame(o, aq.selectedOption);
        verify(aqs).save(aq);
    }

    @Test
    void answerWithNullOptionClearsSelection() {
        TestAttempt a = attempt(1, null, false);
        Question q = question(category(1, "Road signs"));
        AttemptQuestion aq = row(a, q, 1, option(q, "Stop", true));
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(1L, q.questionId)).thenReturn(Optional.of(aq));

        service.answer(1L, new Dto.AnswerRequest(q.questionId, null));

        assertNull(aq.selectedOption);
        verify(aqs).save(aq);
    }

    // ---------- submit & scoring ----------

    private Dto.Result submitWith(TestAttempt a, List<AttemptQuestion> rows) {
        when(attempts.findById(a.attemptId)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdOrderByQuestionOrder(a.attemptId)).thenReturn(rows);
        return service.submit(a.attemptId);
    }

    private static Dto.CategoryResult cat(Dto.Result r, String name) {
        return r.categories().stream().filter(c -> c.category().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void submitAlreadySubmittedIsConflict() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, true)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.submit(1L)));
        verify(attempts, never()).save(any());
    }

    @Test
    void submitMarksAttemptCompletedAndStoresScore() {
        TestAttempt a = attempt(1, null, false);
        List<AttemptQuestion> rows = rows(a, category(1, "Road signs"), answers(28, 0));

        Dto.Result r = submitWith(a, rows);

        assertEquals(28, r.score());
        assertEquals(28, r.total());
        assertTrue(r.passed());
        assertTrue(a.completed);
        assertNotNull(a.submittedAt);
        assertEquals(28, a.score);
        assertTrue(a.passed);
        verify(attempts).save(a);
    }

    @Test
    void fullTestPassesExactlyAtEachPassMark() {
        TestAttempt a = attempt(1, null, false);
        List<AttemptQuestion> rows = new ArrayList<>();
        rows.addAll(rows(a, category(1, "Rules of the road"), answers(22, 6)));
        rows.addAll(rows(a, category(2, "Road signs"), answers(23, 5)));
        rows.addAll(rows(a, category(3, "Controls of the vehicle"), answers(6, 2)));

        Dto.Result r = submitWith(a, rows);

        assertTrue(r.passed());
        assertEquals(51, r.score());
        assertEquals(64, r.total());
        assertEquals(new Dto.CategoryResult("Rules of the road", 22, 28, 22, true), cat(r, "Rules of the road"));
        assertEquals(new Dto.CategoryResult("Road signs", 23, 28, 23, true), cat(r, "Road signs"));
        assertEquals(new Dto.CategoryResult("Controls of the vehicle", 6, 8, 6, true), cat(r, "Controls of the vehicle"));
    }

    @Test
    void failingOneCategoryFailsTheWholeTestEvenWithHighOverallScore() {
        TestAttempt a = attempt(1, null, false);
        List<AttemptQuestion> rows = new ArrayList<>();
        rows.addAll(rows(a, category(1, "Rules of the road"), answers(28, 0)));
        rows.addAll(rows(a, category(2, "Road signs"), answers(28, 0)));
        rows.addAll(rows(a, category(3, "Controls of the vehicle"), answers(5, 3))); // needs 6

        Dto.Result r = submitWith(a, rows);

        assertFalse(r.passed());
        assertFalse(a.passed);
        assertEquals(61, r.score());
        assertFalse(cat(r, "Controls of the vehicle").passed());
        assertTrue(cat(r, "Rules of the road").passed());
    }

    @Test
    void oneBelowPassMarkFails() {
        TestAttempt a = attempt(1, null, false);
        Dto.Result r = submitWith(a, rows(a, category(2, "Road signs"), answers(22, 6))); // needs 23
        assertFalse(r.passed());
        assertEquals(23, cat(r, "Road signs").required());
    }

    @Test
    void passMarkScalesDownWhenFewerQuestionsAreAsked() {
        TestAttempt a = attempt(1, null, false);
        // Rules of the road: 22 of 28. With only 10 asked, required = ceil(22 * 10 / 28) = 8.
        Dto.Result r = submitWith(a, rows(a, category(1, "Rules of the road"), answers(8, 2)));
        assertEquals(new Dto.CategoryResult("Rules of the road", 8, 10, 8, true), cat(r, "Rules of the road"));
        assertTrue(r.passed());
    }

    @Test
    void unknownCategoryUsesDefaultPassMark() {
        TestAttempt a = attempt(1, null, false);
        Dto.Result r = submitWith(a, rows(a, category(9, "Something new"), answers(6, 4))); // default 7 of 10
        assertEquals(7, cat(r, "Something new").required());
        assertFalse(r.passed());
    }

    @Test
    void incorrectListShowsSelectedAndCorrectAnswersAndSkippedQuestions() {
        TestAttempt a = attempt(1, null, false);
        List<AttemptQuestion> rows = rows(a, category(2, "Road signs"), true, false, null);

        Dto.Result r = submitWith(a, rows);

        assertEquals(1, r.score());
        assertEquals(2, r.incorrect().size());

        Dto.IncorrectQuestion wrong = r.incorrect().get(0);
        Question wq = rows.get(1).question;
        assertEquals(wq.questionId, wrong.questionId());
        assertEquals(2, wrong.order());
        assertEquals("Road signs", wrong.category());
        assertEquals(wq.questionText, wrong.text());
        assertEquals("wrong", wrong.selectedText());
        assertEquals(rows.get(1).selectedOption.optionId, wrong.selectedOptionId());
        assertEquals("right", wrong.correctText());
        assertNotNull(wrong.correctOptionId());
        assertEquals(wq.explanation, wrong.explanation());

        Dto.IncorrectQuestion skipped = r.incorrect().get(1);
        assertEquals(3, skipped.order());
        assertNull(skipped.selectedOptionId());
        assertNull(skipped.selectedText());
        assertEquals("right", skipped.correctText());
    }

    // ---------- results ----------

    @Test
    void resultsBeforeSubmitIsConflict() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, false)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.results(1L)));
    }

    @Test
    void resultsReturnsStoredScoreAndBreakdown() {
        TestAttempt a = attempt(1, null, true);
        a.score = 2;
        a.passed = false;
        List<AttemptQuestion> rows = rows(a, category(2, "Road signs"), true, true, false);
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(aqs.findByAttempt_AttemptIdOrderByQuestionOrder(1L)).thenReturn(rows);

        Dto.Result r = service.results(1L);

        assertEquals(1L, r.attemptId());
        assertEquals(2, r.score());
        assertEquals(3, r.total());
        assertFalse(r.passed());
        assertEquals(1, r.incorrect().size());
        assertEquals(1, r.categories().size());
        verify(attempts, never()).save(any());
    }

    // ---------- claim ----------

    @Test
    void claimIncompleteAttemptIsConflict() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, false)));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.claim(1L, 3L)));
    }

    @Test
    void claimSomeoneElsesAttemptIsForbidden() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, user(4), true)));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.claim(1L, 3L)));
        verify(attempts, never()).save(any());
    }

    @Test
    void claimWithUnknownUserIs404() {
        when(attempts.findById(1L)).thenReturn(Optional.of(attempt(1, null, true)));
        when(users.findById(3L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.claim(1L, 3L)));
    }

    @Test
    void claimGuestAttemptLinksItToUser() {
        TestAttempt a = attempt(1, null, true);
        User u = user(3);
        when(attempts.findById(1L)).thenReturn(Optional.of(a));
        when(users.findById(3L)).thenReturn(Optional.of(u));

        service.claim(1L, 3L);

        assertSame(u, a.user);
        verify(attempts).save(a);
    }

    // ---------- history ----------

    @Test
    void historyMapsAttemptsToSummaries() {
        TestAttempt a = attempt(1, user(3), true);
        a.submittedAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        a.score = 50;
        a.passed = false;
        when(attempts.findByUser_UserIdOrderByStartedAtDesc(3L)).thenReturn(List.of(a));

        assertEquals(List.of(new Dto.AttemptSummary(1L, a.startedAt, a.submittedAt, true, 50, false)),
                service.history(3L));
    }
}
