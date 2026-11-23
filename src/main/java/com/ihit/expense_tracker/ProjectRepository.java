package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwnerSubjectOrderByNameAsc(String ownerSubject);
}
