package com.ihit.expense_tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectBudgetTests {

    private ExpenseRepository expenseRepository;
    private ProjectRepository projectRepository;
    private ProjectContributorRepository contributorRepository;
    private UserProfileRepository userProfileRepository;
    private ExpenseCategoryRepository categoryRepository;
    private ProjectCategoryBudgetRepository budgetRepository;
    private ProjectCategoryBudgetItemRepository budgetItemRepository;
    private ExpenseController controller;
    private Project project;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        projectRepository = mock(ProjectRepository.class);
        contributorRepository = mock(ProjectContributorRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        categoryRepository = mock(ExpenseCategoryRepository.class);
        budgetRepository = mock(ProjectCategoryBudgetRepository.class);
        budgetItemRepository = mock(ProjectCategoryBudgetItemRepository.class);
        controller = new ExpenseController(
                expenseRepository,
                projectRepository,
                contributorRepository,
                userProfileRepository,
                categoryRepository,
                budgetRepository,
                budgetItemRepository);

        project = new Project();
        project.setId(7L);
        project.setName("Trip");
        project.setOwnerSubject("owner");
    }

    @Test
    void allowsProjectOwnerToRemoveItsContributor() {
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findById(11L)).thenReturn(Optional.of(contributor));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.removeContributor(7L, 11L, authentication("owner"), redirect);

        assertEquals("redirect:/", result);
        assertEquals("Contributor removed successfully.", redirect.getFlashAttributes().get("successMessage"));
        verify(contributorRepository).delete(contributor);
    }

    @Test
    void preventsNonOwnerFromRemovingContributor() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.removeContributor(7L, 11L, authentication("another-user"), redirect);

        assertEquals("Only the project owner can remove contributors.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(contributorRepository, never()).findById(11L);
        verify(contributorRepository, never()).delete(org.mockito.ArgumentMatchers.any(ProjectContributor.class));
    }

    @Test
    void preventsRemovingContributorBelongingToAnotherProject() {
        Project anotherProject = new Project();
        anotherProject.setId(8L);
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(anotherProject);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findById(11L)).thenReturn(Optional.of(contributor));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.removeContributor(7L, 11L, authentication("owner"), redirect);

        assertEquals("Contributor not found in this project.", redirect.getFlashAttributes().get("errorMessage"));
        verify(contributorRepository, never()).delete(org.mockito.ArgumentMatchers.any(ProjectContributor.class));
    }

    @Test
    void addsContributorFromEmailAndStoresAddedDate() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findByProjectIdOrderByNameAsc(7L)).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.addContributor(7L, "New.User@example.com", authentication("owner"), redirect);

        verify(contributorRepository).save(org.mockito.ArgumentMatchers.argThat(contributor ->
                contributor.getProject().equals(project)
                        && contributor.getName().equals("new.user@example.com")
                        && contributor.getEmail().equals("new.user@example.com")
                        && contributor.isCanViewExpenses()
                        && !contributor.isCanManageExpenses()
                        && LocalDate.now().equals(contributor.getAddedAt())));
        assertEquals("Contributor added successfully.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void allowsProjectOwnerToChangeContributorExpenseAccess() {
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        project.setClosed(true);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findById(11L)).thenReturn(Optional.of(contributor));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.updateContributorPermission(
                7L, 11L, true, true, authentication("owner"), redirect);

        assertTrue(contributor.isCanViewExpenses());
        assertTrue(contributor.isCanManageExpenses());
        verify(contributorRepository).save(contributor);
        assertEquals("Contributor access updated.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void preventsNonOwnerFromChangingContributorExpenseAccess() {
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findById(11L)).thenReturn(Optional.of(contributor));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.updateContributorPermission(
                7L, 11L, true, true, authentication("another-user"), redirect);

        assertTrue(contributor.isCanViewExpenses());
        assertFalse(contributor.isCanManageExpenses());
        verify(contributorRepository, never()).save(org.mockito.ArgumentMatchers.any(ProjectContributor.class));
        assertEquals("Only the project owner can change contributor access.",
                redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void allowsOwnerToSetViewAndSaveEditAccessIndependently() {
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findById(11L)).thenReturn(Optional.of(contributor));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.updateContributorPermission(
                7L, 11L, false, true, authentication("owner"), redirect);

        assertFalse(contributor.isCanViewExpenses());
        assertTrue(contributor.isCanManageExpenses());
        verify(contributorRepository).save(contributor);
    }

    @Test
    void preventsAddingContributorToClosedProject() {
        project.setClosed(true);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.addContributor(7L, "new@example.com", authentication("owner"), redirect);

        assertEquals("Closed projects are read-only. Reopen the project first.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(contributorRepository, never()).save(org.mockito.ArgumentMatchers.any(ProjectContributor.class));
    }

    @Test
    void allowsOwnerToCloseAndReopenProject() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap closeRedirect = new RedirectAttributesModelMap();

        controller.closeProject(7L, authentication("owner"), closeRedirect);

        assertTrue(project.isClosed());
        verify(projectRepository).save(project);
        assertEquals("Project closed. It is now read-only.",
                closeRedirect.getFlashAttributes().get("successMessage"));

        RedirectAttributesModelMap reopenRedirect = new RedirectAttributesModelMap();
        controller.reopenProject(7L, authentication("owner"), reopenRedirect);

        assertFalse(project.isClosed());
        verify(projectRepository, org.mockito.Mockito.times(2)).save(project);
        assertEquals("Project reopened successfully.",
                reopenRedirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void preventsNonOwnerFromReopeningProject() {
        project.setClosed(true);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.reopenProject(7L, authentication("another-user"), redirect);

        assertEquals("Only the project owner can reopen this project.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any(Project.class));
    }

    @Test
    void blocksExpenseChangesAfterProjectIsClosed() {
        project.setClosed(true);
        Expense expense = projectExpense("owner");
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("owner"), redirect);
        controller.deleteExpense(3L, authentication("owner"), redirect);

        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
        verify(expenseRepository, never()).delete(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void blocksNewExpensesInClosedProject() {
        project.setClosed(true);
        Expense expense = projectExpense("owner");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.addExpense(expense,
                new BeanPropertyBindingResult(expense, "expense"),
                redirect, new ExtendedModelMap(), authentication("owner"), false);

        assertEquals("redirect:/expenses", result);
        assertEquals("This project is closed and cannot accept new expenses.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void blocksProjectAndBudgetEditsAfterClosing() {
        project.setClosed(true);
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap editRedirect = new RedirectAttributesModelMap();
        RedirectAttributesModelMap budgetRedirect = new RedirectAttributesModelMap();

        controller.editProject(7L, "Updated trip", authentication("owner"), editRedirect);
        controller.saveProjectBudgetItems(7L, "Food", List.of("Meals"), List.of("20"),
                authentication("owner"), budgetRedirect);

        assertEquals("Closed projects are read-only. Reopen the project first.",
                editRedirect.getFlashAttributes().get("errorMessage"));
        assertEquals("Closed projects are read-only. Reopen the project first.",
                budgetRedirect.getFlashAttributes().get("errorMessage"));
        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any(Project.class));
        verify(budgetItemRepository, never())
                .deleteByProjectIdAndCategoryIgnoreCase(7L, "Food");
        verify(budgetItemRepository, never()).saveAll(anyList());
    }

    @Test
    void allowsProjectOwnerToEditContributorExpenseWithoutChangingOwnership() {
        Expense expense = projectExpense("contributor");
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("owner"), redirect);

        verify(expenseRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getExpenseDate().equals(form.getExpenseDate())
                        && saved.getAmount().compareTo(form.getAmount()) == 0
                        && saved.getParticulars().equals(form.getParticulars())
                        && saved.getCategory().equals(form.getCategory())
                        && saved.getProjectId().equals(7L)
                        && saved.getProjectName().equals("Trip")
                        && saved.getOwnerSubject().equals("contributor")
                        && saved.getAddedByName().equals("Contributor")));
        assertEquals("Expense updated successfully.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void allowsCurrentContributorToEditOwnExpense() {
        Expense expense = projectExpense("contributor");
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        membership.setCanManageExpenses(true);
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("contributor")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("contributor"), redirect);

        verify(expenseRepository).save(expense);
        assertEquals("Expense updated successfully.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void preventsViewOnlyContributorFromEditingOwnExpense() {
        Expense expense = projectExpense("contributor");
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("contributor"), redirect);

        assertEquals("Expense not found or you do not have permission to change it.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void preventsViewOnlyContributorFromAddingExpense() {
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        Expense expense = projectExpense("contributor");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("contributor")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.addExpense(expense,
                new BeanPropertyBindingResult(expense, "expense"),
                redirect, new ExtendedModelMap(), authentication("contributor"), false);

        assertEquals("redirect:/expenses", result);
        assertEquals("You have view-only access to this project.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void hidesProjectExpensesWhenContributorViewAccessIsDisabled() {
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        membership.setCanViewExpenses(false);
        Expense expense = projectExpense("contributor");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        when(expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc("contributor"))
                .thenReturn(List.of(expense));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.expenses(model, authentication("contributor"), null);

        assertEquals(List.of(), model.getAttribute("expenses"));
    }

    @Test
    void allowsContributorWithExpenseAccessToAddExpense() {
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        membership.setCanManageExpenses(true);
        Expense expense = projectExpense("contributor");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("contributor")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.addExpense(expense,
                new BeanPropertyBindingResult(expense, "expense"),
                redirect, new ExtendedModelMap(), authentication("contributor"), false);

        assertEquals("redirect:/expenses", result);
        verify(expenseRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getOwnerSubject().equals("contributor")
                        && saved.getProjectId().equals(7L)
                        && saved.getProjectName().equals("Trip")));
        assertEquals("Expense added successfully.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void preventsContributorFromEditingAnotherUsersExpense() {
        Expense expense = projectExpense("another-contributor");
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        membership.setCanManageExpenses(true);
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("contributor"), redirect);

        assertEquals("Expense not found or you do not have permission to change it.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void preventsRemovedContributorFromEditingTheirFormerProjectExpense() {
        Expense expense = projectExpense("contributor");
        ExpenseController.ExpenseEditForm form = editedExpenseForm();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.editExpense(3L, form, new BeanPropertyBindingResult(form, "updatedExpense"),
                authentication("contributor"), redirect);

        assertEquals("Expense not found or you do not have permission to change it.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any(Expense.class));
    }

    @Test
    void allowsProjectOwnerToDeleteContributorExpense() {
        Expense expense = projectExpense("contributor");
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.deleteExpense(3L, authentication("owner"), redirect);

        verify(expenseRepository).delete(expense);
        assertEquals("Expense deleted successfully.", redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    void showsOwnerNameAndImageFromSavedUserProfile() {
        ProjectContributor membership = new ProjectContributor();
        membership.setProject(project);
        membership.setEmail("contributor@example.com");
        UserProfile profile = new UserProfile();
        profile.setSubject("owner");
        profile.setDisplayName("Profile Owner");
        profile.setImageUrl("https://images.example.com/owner.png");

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(contributorRepository.findByEmailIgnoreCase("contributor@example.com"))
                .thenReturn(List.of(membership));
        when(contributorRepository.findByProjectIdOrderByNameAsc(7L)).thenReturn(List.of());
        when(userProfileRepository.findAllById(List.of("owner"))).thenReturn(List.of(profile));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.projects(model, authentication("contributor"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectCard = ((List<Map<String, Object>>) model.get("projectCards")).get(0);
        assertEquals("Profile Owner", projectCard.get("ownerName"));
        assertEquals("https://images.example.com/owner.png", projectCard.get("ownerImageUrl"));
    }

    @Test
    void addsSignedInUserDetailsToSharedAccountMenuModel() {
        when(userProfileRepository.findById("owner")).thenReturn(Optional.empty());

        ExtendedModelMap model = new ExtendedModelMap();
        controller.addAccountMenuProfile(model, authentication("owner"));

        assertEquals("owner", model.get("username"));
        assertEquals("owner@example.com", model.get("userEmail"));
        assertEquals("https://images.example.com/owner.png", model.get("ownerImageUrl"));
        verify(userProfileRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "owner".equals(saved.getSubject())
                        && "owner@example.com".equals(saved.getEmail())
                        && "https://images.example.com/owner.png".equals(saved.getImageUrl())));
    }

    @Test
    void mapsExpenseSubmittersToTheirSavedProfileImages() {
        Expense expense = new Expense();
        expense.setId(3L);
        expense.setOwnerSubject("owner");
        expense.setAddedByName("Project Owner");
        expense.setExpenseDate(LocalDate.now());
        expense.setAmount(new BigDecimal("12.00"));
        UserProfile profile = new UserProfile();
        profile.setSubject("owner");
        profile.setImageUrl("https://images.example.com/owner.png");
        when(expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc("owner"))
                .thenReturn(List.of(expense));
        when(userProfileRepository.findAllById(List.of("owner"))).thenReturn(List.of(profile));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.dashboard(model, authentication("owner"), null);

        @SuppressWarnings("unchecked")
        Map<String, String> imageUrls = (Map<String, String>) model.get("userProfileImages");
        assertEquals("https://images.example.com/owner.png", imageUrls.get("owner"));
    }

    @Test
    void usesGoogleProfileImageForProjectContributors() {
        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        contributor.setName("Contributor");
        contributor.setEmail("contributor@example.com");
        contributor.setImageUrl("https://images.example.com/old.png");
        ReflectionTestUtils.setField(contributor, "id", 42L);
        UserProfile profile = new UserProfile();
        profile.setSubject("contributor-subject");
        profile.setEmail("CONTRIBUTOR@example.com");
        profile.setDisplayName("Google Contributor");
        profile.setImageUrl("https://images.example.com/google.png");

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(contributorRepository.findByProjectIdOrderByNameAsc(7L)).thenReturn(List.of(contributor));
        when(userProfileRepository.findByEmailIgnoreCaseIn(List.of("contributor@example.com")))
                .thenReturn(List.of(profile));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.projects(model, authentication("owner"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectCard = ((List<Map<String, Object>>) model.get("projectCards")).get(0);
        @SuppressWarnings("unchecked")
        Map<Long, String> imageUrls = (Map<Long, String>) projectCard.get("contributorImageUrls");
        @SuppressWarnings("unchecked")
        Map<Long, String> displayNames = (Map<Long, String>) projectCard.get("contributorDisplayNames");
        assertEquals("https://images.example.com/google.png", imageUrls.get(42L));
        assertEquals("Google Contributor", displayNames.get(42L));
    }

    @Test
    void savesCategoryBudgetAsTheSumOfItsJustificationItems() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of());
        when(budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.saveProjectBudgetItems(
                7L,
                "Food",
                List.of("Breakfast", "Dinner"),
                List.of("25.50", "100.00"),
                authentication("owner"),
                redirect);

        assertEquals("redirect:/projects/7/budget", result);
        assertEquals("Category budget breakdown saved.", redirect.getFlashAttributes().get("successMessage"));
        verify(budgetItemRepository).deleteByProjectIdAndCategoryIgnoreCase(7L, "Food");
        verify(budgetItemRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items ->
                java.util.stream.StreamSupport.stream(items.spliterator(), false).count() == 2));
        verify(budgetRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getCategory().equals("Food")
                        && saved.getAmount().compareTo(new BigDecimal("125.50")) == 0));
    }

    @Test
    void rejectsInvalidBudgetItemWithoutReplacingSavedValues() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of());
        when(budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.saveProjectBudgetItems(
                7L,
                "Food",
                List.of("Breakfast"),
                List.of("0"),
                authentication("owner"),
                redirect);

        assertEquals("Enter a valid item amount greater than zero with up to two decimal places.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(budgetItemRepository, never()).deleteByProjectIdAndCategoryIgnoreCase(7L, "Food");
        verify(budgetItemRepository, never()).saveAll(anyList());
        verify(budgetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preventsNonOwnerFromChangingProjectBudgetItems() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.saveProjectBudgetItems(
                7L,
                "Food",
                List.of("Breakfast"),
                List.of("50"),
                authentication("another-user"),
                redirect);

        assertEquals("Only the project owner can manage its budget.",
                redirect.getFlashAttributes().get("errorMessage"));
        verify(budgetItemRepository, never()).deleteByProjectIdAndCategoryIgnoreCase(7L, "Food");
        verify(budgetItemRepository, never()).saveAll(anyList());
        verify(budgetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preventsTotalBudgetPrecisionOverflow() {
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of());
        when(budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.saveProjectBudgetItems(
                7L,
                "Food",
                List.of("Item A", "Item B"),
                List.of("999999999999.99", "0.01"),
                authentication("owner"),
                redirect);

        assertEquals("The category budget total is too large.", redirect.getFlashAttributes().get("errorMessage"));
        verify(budgetItemRepository, never()).deleteByProjectIdAndCategoryIgnoreCase(7L, "Food");
    }

    @Test
    void calculatesCategorySpendAndRemainingBudgetForProjectCard() {
        Expense expense = new Expense();
        expense.setId(2L);
        expense.setProjectId(7L);
        expense.setProjectName("Trip");
        expense.setOwnerSubject("contributor");
        expense.setExpenseDate(LocalDate.now());
        expense.setCategory("Food");
        expense.setAmount(new BigDecimal("40.00"));
        ProjectCategoryBudget budget = new ProjectCategoryBudget();
        budget.setProjectId(7L);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("100.00"));

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of(expense));
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());
        when(expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc("owner")).thenReturn(List.of());
        when(contributorRepository.findByProjectIdOrderByNameAsc(7L)).thenReturn(List.of());
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of(budget));
        when(budgetItemRepository.findByProjectIdAndCategoryIgnoreCaseOrderByItemNameAsc(7L, "Food"))
                .thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        controller.projects(model, authentication("owner"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectCard = ((List<Map<String, Object>>) model.get("projectCards")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> categoryLine = ((List<Map<String, Object>>) projectCard.get("budgetLines")).get(0);
        assertEquals(0, ((BigDecimal) projectCard.get("budgetRemaining")).compareTo(new BigDecimal("60.00")));
        assertEquals(0, ((BigDecimal) categoryLine.get("spent")).compareTo(new BigDecimal("40.00")));
        assertEquals(0, ((BigDecimal) categoryLine.get("remaining")).compareTo(new BigDecimal("60.00")));
    }

    @Test
    void includesSavedBudgetJustificationInCategoryRows() {
        ProjectCategoryBudget budget = new ProjectCategoryBudget();
        budget.setProjectId(7L);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("100.00"));
        budget.setJustification("Meals for the travel group");

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of(budget));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.projectBudget(7L, authentication("owner"), model,
                new RedirectAttributesModelMap());

        assertEquals("project-budget", view);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) model.get("budgetRows")).stream()
                .filter(item -> item.get("category").equals("Food"))
                .findFirst()
                .orElseThrow();
        assertEquals("Meals for the travel group", row.get("justification"));
    }

    @Test
    void categoryBudgetOnPageUsesSumOfItsJustificationItems() {
        ProjectCategoryBudget budget = new ProjectCategoryBudget();
        budget.setProjectId(7L);
        budget.setCategory("Food");
        budget.setAmount(new BigDecimal("125.50"));
        ProjectCategoryBudgetItem first = budgetItem("Breakfast", "25.50");
        ProjectCategoryBudgetItem second = budgetItem("Dinner", "100.00");

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(budgetRepository.findByProjectIdOrderByCategoryAsc(7L)).thenReturn(List.of(budget));
        when(categoryRepository.findByOwnerSubjectOrderByNameAsc("owner")).thenReturn(List.of());
        when(budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(7L))
                .thenReturn(List.of(first, second));
        when(expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(7L)).thenReturn(List.of());
        when(expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc("Trip")).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        controller.projectBudget(7L, authentication("owner"), model, new RedirectAttributesModelMap());

        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) model.get("budgetRows")).stream()
                .filter(item -> item.get("category").equals("Food"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, ((BigDecimal) row.get("amount")).compareTo(new BigDecimal("125.50")));
        assertEquals(2, ((List<?>) row.get("items")).size());
    }

    private ProjectCategoryBudgetItem budgetItem(String name, String amount) {
        ProjectCategoryBudgetItem item = new ProjectCategoryBudgetItem();
        item.setProjectId(7L);
        item.setCategory("Food");
        item.setItemName(name);
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    private Expense projectExpense(String submitter) {
        Expense expense = new Expense();
        expense.setId(3L);
        expense.setProjectId(7L);
        expense.setProjectName("Trip");
        expense.setOwnerSubject(submitter);
        expense.setAddedByName("Contributor");
        expense.setExpenseDate(LocalDate.of(2025, 1, 1));
        expense.setAmount(new BigDecimal("10.00"));
        expense.setParticulars("Old expense");
        expense.setCategory("Food");
        return expense;
    }

    private ExpenseController.ExpenseEditForm editedExpenseForm() {
        ExpenseController.ExpenseEditForm form = new ExpenseController.ExpenseEditForm();
        form.setExpenseDate(LocalDate.of(2025, 2, 1));
        form.setAmount(new BigDecimal("25.50"));
        form.setParticulars("Updated expense");
        form.setCategory("Transport");
        return form;
    }

    private UsernamePasswordAuthenticationToken authentication(String subject) {
        var authority = new SimpleGrantedAuthority("ROLE_USER");
        var principal = new DefaultOAuth2User(
                List.of(authority),
                Map.of(
                        "sub", subject,
                        "name", subject,
                        "email", subject + "@example.com",
                        "picture", "https://images.example.com/" + subject + ".png"),
                "sub");
        return new UsernamePasswordAuthenticationToken(principal, "N/A", List.of(authority));
    }
}
