package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectDepositRepository extends JpaRepository<ProjectDeposit, Long> {
    List<ProjectDeposit> findByProjectIdOrderByDepositDateDescIdDesc(Long projectId);
}
