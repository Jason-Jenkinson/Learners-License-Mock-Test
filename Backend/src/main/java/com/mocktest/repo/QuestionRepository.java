package com.mocktest.repo;

import com.mocktest.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query("select q from Question q where q.active = true and q.verificationStatus = 'Verified' and q.category.categoryId = :cid")
    List<Question> findEligible(@Param("cid") Long categoryId);

    List<Question> findByQuestionTextIgnoreCase(String text);
}