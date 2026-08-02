package com.aipr.api.repository;

import com.aipr.api.model.PullRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestEventRepository extends JpaRepository<PullRequestEvent, Long> {
}
