package com.mocktest;

import com.mocktest.model.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Small helpers shared by the unit tests. */
public final class TestData {
    private TestData() {}

    public static Category category(long id, String name) {
        Category c = new Category();
        c.categoryId = id;
        c.categoryName = name;
        return c;
    }

    public static User user(long id, String username) {
        User u = new User();
        u.userId = id;
        u.username = username;
        return u;
    }

    public static Question question(long id, Category category) {
        Question q = new Question();
        q.questionId = id;
        q.category = category;
        q.questionText = "Question " + id;
        q.explanation = "Because";
        q.source = "test";
        return q;
    }

    public static AnswerOption option(long id, Question question, boolean correct) {
        AnswerOption o = new AnswerOption();
        o.optionId = id;
        o.question = question;
        o.optionText = "Option " + id;
        o.correct = correct;
        return o;
    }

    public static TestAttempt attempt(long id, User user) {
        TestAttempt a = new TestAttempt();
        a.attemptId = id;
        a.user = user;
        return a;
    }

    public static AttemptQuestion attemptQuestion(TestAttempt attempt, Question question, int order, AnswerOption selected) {
        AttemptQuestion aq = new AttemptQuestion();
        aq.attempt = attempt;
        aq.question = question;
        aq.questionOrder = order;
        aq.selectedOption = selected;
        return aq;
    }

    /** A fake HttpSession backed by the given map. */
    public static HttpSession session(Map<String, Object> store) {
        HttpSession s = mock(HttpSession.class);
        when(s.getAttribute(anyString())).thenAnswer(i -> store.get(i.getArgument(0)));
        doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(s).setAttribute(anyString(), any());
        return s;
    }

    public static void assertStatus(int expected, Executable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, ex.getStatusCode().value());
    }
}
