package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findAllByOrderByExpenseDateDescIdDesc();
    List<Expense> findByOwnerSubjectOrderByExpenseDateDescIdDesc(String ownerSubject);
    List<Expense> findByOwnerSubjectAndProjectNameOrderByExpenseDateDescIdDesc(String ownerSubject, String projectName);
    List<Expense> findByProjectIdOrderByExpenseDateDescIdDesc(Long projectId);
    List<Expense> findByProjectNameOrderByExpenseDateDescIdDesc(String projectName);
}
