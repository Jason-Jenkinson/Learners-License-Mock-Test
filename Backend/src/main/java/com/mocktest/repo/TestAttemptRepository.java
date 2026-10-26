package com.mocktest.repo;

import com.mocktest.model.TestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {
    List<TestAttempt> findByUser_UserIdOrderByStartedAtDesc(Long userId);
}