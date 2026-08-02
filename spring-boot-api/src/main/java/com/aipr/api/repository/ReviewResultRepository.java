package com.aipr.api.repository;

import com.aipr.api.model.ReviewResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewResultRepository extends JpaRepository<ReviewResult, Long> {
    Optional<ReviewResult> findByPullRequestEventId(Long pullRequestEventId);
}
