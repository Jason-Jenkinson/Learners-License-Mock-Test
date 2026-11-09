package com.mocktest.repo;

import com.mocktest.model.AnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {
    List<AnswerOption> findByQuestion_QuestionId(Long questionId);
}