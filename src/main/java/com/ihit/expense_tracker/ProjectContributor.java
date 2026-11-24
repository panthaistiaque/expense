package com.ihit.expense_tracker;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;

@Entity
public class ProjectContributor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Project project;

    private String name;

    @Email(message = "Enter a valid email")
    private String email;

    private String imageUrl;

    private LocalDate addedAt;

    private Boolean canViewExpenses = true;

    private Boolean canManageExpenses = false;

    public ProjectContributor() {
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDate getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDate addedAt) {
        this.addedAt = addedAt;
    }

    public boolean isCanViewExpenses() {
        return !Boolean.FALSE.equals(canViewExpenses);
    }

    public void setCanViewExpenses(boolean canViewExpenses) {
        this.canViewExpenses = canViewExpenses;
    }

    public boolean isCanManageExpenses() {
        return Boolean.TRUE.equals(canManageExpenses);
    }

    public void setCanManageExpenses(boolean canManageExpenses) {
        this.canManageExpenses = canManageExpenses;
    }
}
