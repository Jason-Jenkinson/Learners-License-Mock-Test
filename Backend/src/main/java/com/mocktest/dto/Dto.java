package com.mocktest.dto;

import java.time.LocalDateTime;
import java.util.List;

public class Dto {
    public record Credentials(String username, String password) {}
    public record OptionRequest(String text, boolean correct) {}
    public record QuestionRequest(Long categoryId, String questionText, String imagePath,
                                  String explanation, String source, List<OptionRequest> options) {}
    public record AnswerRequest(Long questionId, Long optionId) {}

    public record OptionView(Long optionId, String text) {}
    public record QuestionView(Long questionId, int order, String text, String imagePath, List<OptionView> options) {}
    public record TestView(Long attemptId, List<QuestionView> questions) {}

    public record CategoryResult(String category, int correct, int asked, int required, boolean passed) {}
    public record IncorrectQuestion(Long questionId, String text, Long selectedOptionId, String selectedText,
                                    Long correctOptionId, String correctText, String explanation) {}
    public record Result(Long attemptId, int score, int total, boolean passed,
                         List<CategoryResult> categories, List<IncorrectQuestion> incorrect) {}
    public record AttemptSummary(Long attemptId, LocalDateTime startedAt, LocalDateTime submittedAt,
                                 boolean completed, Integer score, Boolean passed) {}

    public record AdminOption(Long optionId, String text, boolean correct) {}
    public record AdminQuestion(Long questionId, String category, String text, String source,
                                String status, boolean active, List<AdminOption> options) {}
}