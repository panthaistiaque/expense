package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectContributorRepository extends JpaRepository<ProjectContributor, Long> {
    List<ProjectContributor> findByProjectIdOrderByNameAsc(Long projectId);
    List<ProjectContributor> findByEmailIgnoreCase(String email);
}
