package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectCategoryBudgetRepository extends JpaRepository<ProjectCategoryBudget, Long> {
    List<ProjectCategoryBudget> findByProjectIdOrderByCategoryAsc(Long projectId);
    void deleteByProjectId(Long projectId);
    void deleteByProjectIdAndCategoryIgnoreCase(Long projectId, String category);
}
