package com.mocktest.service;

import com.mocktest.dto.Dto;
import com.mocktest.model.*;
import com.mocktest.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class QuestionService {
    private final QuestionRepository questions;
    private final AnswerOptionRepository options;
    private final CategoryRepository categories;

    public QuestionService(QuestionRepository q, AnswerOptionRepository o, CategoryRepository c) {
        this.questions = q; this.options = o; this.categories = c;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    @Transactional
    public Question create(Dto.QuestionRequest r, Long ignoreDuplicateOf) {
        if (r.questionText() == null || r.questionText().isBlank()) throw bad("Question text is required");
        if (r.source() == null || r.source().isBlank()) throw bad("Source is required");
        if (r.options() == null || r.options().size() < 2) throw bad("At least two answer options are required");
        if (r.options().stream().filter(Dto.OptionRequest::correct).count() != 1)
            throw bad("Exactly one option must be correct");
        Category cat = categories.findById(r.categoryId()).orElseThrow(() -> bad("Unknown category"));

        boolean duplicate = questions.findByQuestionTextIgnoreCase(r.questionText().trim()).stream()
                .anyMatch(q -> q.active && !q.questionId.equals(ignoreDuplicateOf));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate question already exists");

        Question q = new Question();
        q.category = cat;
        q.questionText = r.questionText().trim();
        q.imagePath = r.imagePath();
        q.explanation = r.explanation();
        q.source = r.source();
        q.verificationStatus = "Unverified";
        q.active = true;
        q = questions.save(q);
        for (Dto.OptionRequest o : r.options()) {
            AnswerOption ao = new AnswerOption();
            ao.question = q; ao.optionText = o.text(); ao.correct = o.correct();
            options.save(ao);
        }
        return q;
    }

    public Question get(Long id) {
        return questions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    @Transactional
    public Question setStatus(Long id, String status) {
        if (!Set.of("Unverified", "Verified", "Rejected").contains(status)) throw bad("Invalid status");
        Question q = get(id);
        q.verificationStatus = status;
        return questions.save(q);
    }

    @Transactional
    public Question retire(Long id) {
        Question q = get(id);
        q.active = false;
        return questions.save(q);
    }

    /** Adds a corrected version and retires the old one, so past tests still point at what the learner saw. */
    @Transactional
    public Question correct(Long oldId, Dto.QuestionRequest r) {
        Question old = get(oldId);
        Question fresh = create(r, oldId);
        old.active = false;
        questions.save(old);
        return fresh;
    }

    @Transactional(readOnly = true)
    public List<Dto.AdminQuestion> list() {
        return questions.findAll().stream().map(q -> new Dto.AdminQuestion(
                q.questionId, q.category.categoryName, q.questionText, q.source, q.verificationStatus, q.active,
                options.findByQuestion_QuestionId(q.questionId).stream()
                        .map(o -> new Dto.AdminOption(o.optionId, o.optionText, o.correct)).toList()
        )).toList();
    }
}