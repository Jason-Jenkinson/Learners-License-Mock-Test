package com.mocktest.service;

import com.mocktest.dto.Dto;
import com.mocktest.model.*;
import com.mocktest.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TestService {
    record Rule(int count, int passMark) {}

    // EDIT THESE: questions asked and minimum correct per category (matched by category name).
    static final Map<String, Rule> RULES = Map.of(
            "Rules of the road", new Rule(28, 22),
            "Road signs", new Rule(28, 23),
            "Controls of the vehicle", new Rule(8, 6));
    static final Rule DEFAULT = new Rule(10, 7);

    private final TestAttemptRepository attempts;
    private final AttemptQuestionRepository aqs;
    private final QuestionRepository questions;
    private final AnswerOptionRepository options;
    private final CategoryRepository categories;
    private final UserRepository users;

    public TestService(TestAttemptRepository a, AttemptQuestionRepository aq, QuestionRepository q,
                       AnswerOptionRepository o, CategoryRepository c, UserRepository u) {
        this.attempts = a; this.aqs = aq; this.questions = q; this.options = o; this.categories = c; this.users = u;
    }

    private static ResponseStatusException status(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    public TestAttempt getAttempt(Long id) {
        return attempts.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND, "Attempt not found"));
    }

    @Transactional
    public Dto.TestView start(Long userId) {
        TestAttempt a = new TestAttempt();
        a.user = userId == null ? null : users.findById(userId).orElse(null);
        a = attempts.save(a);

        List<Question> picked = new ArrayList<>();
        for (Category c : categories.findAll()) {
            Rule r = RULES.getOrDefault(c.categoryName, DEFAULT);
            List<Question> pool = new ArrayList<>(questions.findEligible(c.categoryId));
            Collections.shuffle(pool);
            picked.addAll(pool.stream().limit(r.count()).toList());
        }
        if (picked.isEmpty()) throw status(HttpStatus.CONFLICT, "No verified questions available");
        Collections.shuffle(picked);

        List<Dto.QuestionView> views = new ArrayList<>();
        int order = 1;
        for (Question q : picked) {
            AttemptQuestion aq = new AttemptQuestion();
            aq.attempt = a; aq.question = q; aq.questionOrder = order++;
            aqs.save(aq);
            List<AnswerOption> opts = new ArrayList<>(options.findByQuestion_QuestionId(q.questionId));
            Collections.shuffle(opts);
            views.add(new Dto.QuestionView(q.questionId, aq.questionOrder, q.questionText, q.imagePath,
                    opts.stream().map(o -> new Dto.OptionView(o.optionId, o.optionText)).toList()));
        }
        return new Dto.TestView(a.attemptId, views);
    }

    @Transactional
    public void answer(Long attemptId, Dto.AnswerRequest r) {
        TestAttempt a = getAttempt(attemptId);
        if (a.completed) throw status(HttpStatus.CONFLICT, "Test already submitted");
        AttemptQuestion aq = aqs.findByAttempt_AttemptIdAndQuestion_QuestionId(attemptId, r.questionId())
                .orElseThrow(() -> status(HttpStatus.NOT_FOUND, "Question is not part of this attempt"));
        if (r.optionId() == null) {
            aq.selectedOption = null;
        } else {
            AnswerOption o = options.findById(r.optionId())
                    .orElseThrow(() -> status(HttpStatus.BAD_REQUEST, "Unknown option"));
            if (!o.question.questionId.equals(aq.question.questionId))
                throw status(HttpStatus.BAD_REQUEST, "Option does not belong to this question");
            aq.selectedOption = o;
        }
        aqs.save(aq);
    }

    @Transactional
    public Dto.Result submit(Long attemptId) {
        TestAttempt a = getAttempt(attemptId);
        if (a.completed) throw status(HttpStatus.CONFLICT, "Test already submitted");
        Dto.Result r = compute(a);
        a.completed = true;
        a.submittedAt = LocalDateTime.now();
        a.score = r.score();
        a.passed = r.passed();
        attempts.save(a);
        return r;
    }

    @Transactional(readOnly = true)
    public Dto.Result results(Long attemptId) {
        TestAttempt a = getAttempt(attemptId);
        if (!a.completed) throw status(HttpStatus.CONFLICT, "Test not submitted yet");
        Dto.Result r = compute(a);
        return new Dto.Result(a.attemptId, a.score, r.total(), a.passed, r.categories(), r.incorrect());
    }

    @Transactional(readOnly = true)
    public List<Dto.AttemptSummary> history(Long userId) {
        return attempts.findByUser_UserIdOrderByStartedAtDesc(userId).stream()
                .map(a -> new Dto.AttemptSummary(a.attemptId, a.startedAt, a.submittedAt, a.completed, a.score, a.passed))
                .toList();
    }

    private Dto.Result compute(TestAttempt a) {
        List<AttemptQuestion> rows = aqs.findByAttempt_AttemptIdOrderByQuestionOrder(a.attemptId);
        Map<String, int[]> byCat = new LinkedHashMap<>(); // [correct, asked]
        List<Dto.IncorrectQuestion> wrong = new ArrayList<>();
        int score = 0;
        for (AttemptQuestion aq : rows) {
            Question q = aq.question;
            int[] t = byCat.computeIfAbsent(q.category.categoryName, k -> new int[2]);
            t[1]++;
            boolean ok = aq.selectedOption != null && aq.selectedOption.correct;
            if (ok) {
                t[0]++; score++;
            } else {
                AnswerOption right = options.findByQuestion_QuestionId(q.questionId).stream()
                        .filter(o -> o.correct).findFirst().orElse(null);
                AnswerOption sel = aq.selectedOption;
                wrong.add(new Dto.IncorrectQuestion(q.questionId, q.questionText,
                        sel == null ? null : sel.optionId, sel == null ? null : sel.optionText,
                        right == null ? null : right.optionId, right == null ? null : right.optionText,
                        q.explanation));
            }
        }
        List<Dto.CategoryResult> cats = new ArrayList<>();
        boolean passed = true;
        for (var e : byCat.entrySet()) {
            Rule r = RULES.getOrDefault(e.getKey(), DEFAULT);
            int correct = e.getValue()[0], asked = e.getValue()[1];
            int required = (int) Math.ceil(r.passMark() * (double) asked / r.count()); // scales if bank is small
            boolean ok = correct >= required;
            passed &= ok;
            cats.add(new Dto.CategoryResult(e.getKey(), correct, asked, required, ok));
        }
        return new Dto.Result(a.attemptId, score, rows.size(), passed, cats, wrong);
    }
}