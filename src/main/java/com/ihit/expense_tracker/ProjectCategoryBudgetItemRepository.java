package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectCategoryBudgetItemRepository extends JpaRepository<ProjectCategoryBudgetItem, Long> {
    List<ProjectCategoryBudgetItem> findByProjectIdOrderByCategoryAscItemNameAsc(Long projectId);
    List<ProjectCategoryBudgetItem> findByProjectIdAndCategoryIgnoreCaseOrderByItemNameAsc(Long projectId, String category);
    void deleteByProjectIdAndCategoryIgnoreCase(Long projectId, String category);
}
