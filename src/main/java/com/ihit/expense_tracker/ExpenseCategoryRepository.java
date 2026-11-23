package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByOwnerSubjectOrderByNameAsc(String ownerSubject);
    boolean existsByOwnerSubjectAndNameIgnoreCase(String ownerSubject, String name);
}
