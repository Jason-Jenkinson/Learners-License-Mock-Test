package com.mocktest.repo;

import com.mocktest.model.AttemptQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {
    List<AttemptQuestion> findByAttempt_AttemptIdOrderByQuestionOrder(Long attemptId);
    Optional<AttemptQuestion> findByAttempt_AttemptIdAndQuestion_QuestionId(Long attemptId, Long questionId);
}