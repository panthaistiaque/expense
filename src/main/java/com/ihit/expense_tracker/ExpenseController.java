package com.ihit.expense_tracker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
public class ExpenseController {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Food", "Transport", "Shopping", "Utilities",
            "Health", "Education", "Entertainment", "Other");
    private static final Pattern CONTRIBUTOR_EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectContributorRepository contributorRepository;
    private final UserProfileRepository userProfileRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ProjectCategoryBudgetRepository categoryBudgetRepository;
    private final ProjectCategoryBudgetItemRepository budgetItemRepository;

    public ExpenseController(ExpenseRepository expenseRepository,
                             ProjectRepository projectRepository,
                             ProjectContributorRepository contributorRepository,
                             UserProfileRepository userProfileRepository,
                             ExpenseCategoryRepository categoryRepository,
                             ProjectCategoryBudgetRepository categoryBudgetRepository,
                             ProjectCategoryBudgetItemRepository budgetItemRepository) {
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
        this.contributorRepository = contributorRepository;
        this.userProfileRepository = userProfileRepository;
        this.categoryRepository = categoryRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.budgetItemRepository = budgetItemRepository;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/projects";
    }

    @ModelAttribute
    public void addAccountMenuProfile(Model model, Authentication authentication) {
        UserProfile profile = synchronizeUserProfile(authentication);
        model.addAttribute("username", profile != null && profile.getDisplayName() != null
                ? profile.getDisplayName()
                : getUsername(authentication));
        model.addAttribute("ownerImageUrl", profile == null ? getUserImage(authentication) : profile.getImageUrl());
        model.addAttribute("userEmail", profile == null ? getUserEmail(authentication) : profile.getEmail());
        model.addAttribute("databaseSearchAllowed", DatabaseSearchAccess.isAllowed(authentication));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            Authentication authentication,
                            @RequestParam(required = false) String project) {
        model.addAttribute("username", getUsername(authentication));

        String ownerSubject = getOwnerSubject(authentication);
        List<Project> visibleProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
        List<String> projects = visibleProjects.stream().map(Project::getName).distinct().toList();
        List<Expense> allExpenses = getVisibleExpenses(ownerSubject, getUserEmail(authentication), visibleProjects);
        String selectedProject = project != null && projects.contains(project) ? project : "";
        List<Expense> expenses = allExpenses;
        if (!selectedProject.isEmpty()) {
            Project selected = visibleProjects.stream()
                    .filter(item -> selectedProject.equals(item.getName()))
                    .findFirst()
                    .orElse(null);
            expenses = selected == null
                    ? List.of()
                    : getExpensesForProject(selected);
        }
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        BigDecimal todayTotal = expenses.stream()
                .filter(expense -> today.equals(expense.getExpenseDate()))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthTotal = expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null
                        && YearMonth.from(expense.getExpenseDate()).equals(currentMonth))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("todayTotal", todayTotal);
        model.addAttribute("monthTotal", monthTotal);
        model.addAttribute("total", total);
        model.addAttribute("expenseCount", expenses.size());
        List<Expense> recentExpenses = expenses.stream().limit(5).toList();
        model.addAttribute("recentExpenses", recentExpenses);
        model.addAttribute("userProfileImages", getExpenseSubmitterImages(recentExpenses));
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProject", selectedProject);
        return "dashboard";
    }

    @GetMapping("/expenses")
    public String expenses(Model model,
                           Authentication authentication,
                           @RequestParam(required = false) String project) {
        model.addAttribute("username", getUsername(authentication));
        String ownerSubject = getOwnerSubject(authentication);
        List<Project> visibleProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
        List<String> projects = visibleProjects.stream().map(Project::getName).distinct().toList();
        List<Expense> allExpenses = getVisibleExpenses(ownerSubject, getUserEmail(authentication), visibleProjects);
        String selectedProject = project != null && projects.contains(project) ? project : "";
        List<Expense> expenses = allExpenses;
        if (!selectedProject.isEmpty()) {
            Project selected = visibleProjects.stream()
                    .filter(item -> selectedProject.equals(item.getName()))
                    .findFirst()
                    .orElse(null);
            expenses = selected == null ? List.of() : getExpensesForProject(selected);
        }
        if (!model.containsAttribute("expense")) {
            Expense expense = new Expense();
            expense.setExpenseDate(LocalDate.now());
            model.addAttribute("expense", expense);
        }

        model.addAttribute("expenses", expenses);
        model.addAttribute("userProfileImages", getExpenseSubmitterImages(expenses));
        model.addAttribute("editableExpenseIds",
                getEditableExpenseIds(expenses, ownerSubject, getUserEmail(authentication), visibleProjects));
        model.addAttribute("projects", projects);
        model.addAttribute("projectNames",
                getWritableProjectNames(visibleProjects, ownerSubject, getUserEmail(authentication)));
        model.addAttribute("categories", getCategoryNames(ownerSubject));
        model.addAttribute("selectedProject", selectedProject);
        model.addAttribute("total", expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return "expenses";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/categories")
    public String categories(Model model, Authentication authentication) {
        String ownerSubject = getOwnerSubject(authentication);
        model.addAttribute("username", getUsername(authentication));
        model.addAttribute("category", new ExpenseCategory());
        model.addAttribute("defaultCategories", DEFAULT_CATEGORIES);
        model.addAttribute("customCategories", categoryRepository.findByOwnerSubjectOrderByNameAsc(ownerSubject));
        return "categories";
    }

    @PostMapping("/categories")
    public String addCategory(@RequestParam String name,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String normalizedName = name == null ? "" : name.trim();
        String ownerSubject = getOwnerSubject(authentication);
        if (normalizedName.isBlank() || normalizedName.length() > 60) {
            redirectAttributes.addFlashAttribute("errorMessage", "Enter a category name up to 60 characters long.");
            return "redirect:/categories";
        }
        if (DEFAULT_CATEGORIES.stream().anyMatch(category -> category.equalsIgnoreCase(normalizedName))
                || categoryRepository.existsByOwnerSubjectAndNameIgnoreCase(ownerSubject, normalizedName)) {
            redirectAttributes.addFlashAttribute("errorMessage", "That category already exists.");
            return "redirect:/categories";
        }

        ExpenseCategory category = new ExpenseCategory();
        category.setName(normalizedName);
        category.setOwnerSubject(ownerSubject);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Category added successfully.");
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        ExpenseCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null || !ownerSubject.equals(category.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Category not found or you do not have permission to delete it.");
            return "redirect:/categories";
        }
        categoryRepository.delete(category);
        redirectAttributes.addFlashAttribute("successMessage", "Category deleted. Existing expenses keep their saved category name.");
        return "redirect:/categories";
    }

    @GetMapping("/projects")
    public String projects(Model model, Authentication authentication) {
        String ownerSubject = getOwnerSubject(authentication);
        List<Project> savedProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
        model.addAttribute("username", getUsername(authentication));
        model.addAttribute("ownerImageUrl", getUserImage(authentication));
        model.addAttribute("project", new Project());
        Expense expense = new Expense();
        expense.setExpenseDate(LocalDate.now());
        model.addAttribute("expense", expense);
        model.addAttribute("projects", savedProjects);
        model.addAttribute("projectNames",
                getWritableProjectNames(savedProjects, ownerSubject, getUserEmail(authentication)));
        model.addAttribute("categories", getCategoryNames(ownerSubject));
        model.addAttribute("budgetCategories", getBudgetCategoryNames(ownerSubject, savedProjects));
        model.addAttribute("projectCards", projectCards(ownerSubject, getUserEmail(authentication)));
        model.addAttribute("currentOwnerSubject", ownerSubject);
        return "projects";
    }

    @PostMapping("/projects")
    public String addProject(@Valid @ModelAttribute("project") Project project,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model,
                             Authentication authentication) {
        String ownerSubject = getOwnerSubject(authentication);
        if (project.getName() != null) {
            project.setName(project.getName().trim());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("username", getUsername(authentication));
            model.addAttribute("ownerImageUrl", getUserImage(authentication));
            Expense expense = new Expense();
            expense.setExpenseDate(LocalDate.now());
            List<Project> savedProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
            model.addAttribute("expense", expense);
            model.addAttribute("projects", savedProjects);
            model.addAttribute("projectNames",
                    getWritableProjectNames(savedProjects, ownerSubject, getUserEmail(authentication)));
            model.addAttribute("categories", getCategoryNames(ownerSubject));
            model.addAttribute("budgetCategories", getBudgetCategoryNames(
                    ownerSubject, savedProjects));
            model.addAttribute("projectCards", projectCards(ownerSubject, getUserEmail(authentication)));
            model.addAttribute("currentOwnerSubject", ownerSubject);
            model.addAttribute("openProjectModal", true);
            return "projects";
        }

        project.setOwnerSubject(ownerSubject);
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("successMessage", "Project added successfully.");
        return "redirect:/";
    }

    @PostMapping("/projects/{id}/edit")
    public String editProject(@PathVariable Long id,
                              @RequestParam String name,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !ownerSubject.equals(project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can edit this project.");
            return "redirect:/";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Closed projects are read-only. Reopen the project first.");
            return "redirect:/";
        }
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Project name is required.");
            return "redirect:/";
        }
        project.setName(name.trim());
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("successMessage", "Project updated successfully.");
        return "redirect:/";
    }

    @PostMapping("/projects/{id}/close")
    public String closeProject(@PathVariable Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !Objects.equals(ownerSubject, project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can close this project.");
            return "redirect:/projects";
        }
        if (!project.isClosed()) {
            project.setClosed(true);
            projectRepository.save(project);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Project closed. It is now read-only.");
        return "redirect:/projects";
    }

    @PostMapping("/projects/{id}/reopen")
    public String reopenProject(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !Objects.equals(ownerSubject, project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can reopen this project.");
            return "redirect:/projects";
        }
        if (project.isClosed()) {
            project.setClosed(false);
            projectRepository.save(project);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Project reopened successfully.");
        return "redirect:/projects";
    }

    @GetMapping("/projects/{id}/budget")
    public String projectBudget(@PathVariable Long id,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !ownerSubject.equals(project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can manage its budget.");
            return "redirect:/projects";
        }

        Map<String, ProjectCategoryBudget> savedBudgets = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        categoryBudgetRepository.findByProjectIdOrderByCategoryAsc(id)
                .forEach(budget -> savedBudgets.put(budget.getCategory(), budget));
        Map<String, List<ProjectCategoryBudgetItem>> itemsByCategory =
                new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(id)
                .forEach(item -> itemsByCategory.computeIfAbsent(item.getCategory(), key -> new java.util.ArrayList<>()).add(item));
        Map<String, BigDecimal> spentByCategory = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Expense> projectExpenses = getExpensesForProject(project).stream()
                .filter(expense -> id.equals(expense.getProjectId())
                        || (expense.getProjectId() == null
                        && project.getName().equals(expense.getProjectName())))
                .toList();
        projectExpenses.stream()
                .filter(expense -> expense.getCategory() != null)
                .forEach(expense -> spentByCategory.merge(
                        expense.getCategory(), expense.getAmount(), BigDecimal::add));

        java.util.Set<String> budgetCategoryNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        budgetCategoryNames.addAll(getBudgetCategoryNames(ownerSubject, List.of(project)));
        budgetCategoryNames.addAll(spentByCategory.keySet());
        budgetCategoryNames.addAll(itemsByCategory.keySet());
        budgetCategoryNames.addAll(savedBudgets.keySet());
        List<Map<String, Object>> budgetRows = budgetCategoryNames.stream()
                .map(categoryName -> {
                    ProjectCategoryBudget budget = savedBudgets.get(categoryName);
                    List<ProjectCategoryBudgetItem> items = itemsByCategory.getOrDefault(categoryName, List.of());
                    BigDecimal spent = spentByCategory.getOrDefault(categoryName, BigDecimal.ZERO);
                    BigDecimal categoryBudget = items.isEmpty()
                            ? (budget == null ? null : budget.getAmount())
                            : items.stream().map(ProjectCategoryBudgetItem::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", categoryName);
                    row.put("spent", spent);
                    row.put("amount", categoryBudget);
                    row.put("remaining", categoryBudget == null ? null : categoryBudget.subtract(spent));
                    row.put("justification", budget == null ? "" : budget.getJustification());
                    row.put("items", items);
                    row.put("itemCount", items.isEmpty() && budget != null ? 1 : items.size());
                    return row;
                })
                .toList();
        BigDecimal budgetTotal = budgetRows.stream()
                .map(row -> (BigDecimal) row.get("amount"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = projectExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("username", getUsername(authentication));
        model.addAttribute("ownerImageUrl", getUserImage(authentication));
        model.addAttribute("project", project);
        model.addAttribute("budgetRows", budgetRows);
        model.addAttribute("budgetTotal", budgetTotal);
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("budgetRemaining", budgetTotal.subtract(totalSpent));
        return "project-budget";
    }

    @PostMapping("/projects/{id}/budget/items")
    @Transactional
    public String saveProjectBudgetItems(@PathVariable Long id,
                                         @RequestParam String category,
                                         @RequestParam(required = false) List<String> itemName,
                                         @RequestParam(required = false) List<String> itemAmount,
                                         Authentication authentication,
                                         RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !ownerSubject.equals(project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can manage its budget.");
            return "redirect:/projects";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Closed projects are read-only. Reopen the project first.");
            return "redirect:/projects/" + id + "/budget";
        }
        String budgetPage = "redirect:/projects/" + id + "/budget";
        String categoryName = category.trim();

        java.util.Set<String> allowedCategories = new java.util.HashSet<>(
                getCategoryNames(ownerSubject).stream().map(name -> name.toLowerCase(java.util.Locale.ROOT)).toList());
        categoryBudgetRepository.findByProjectIdOrderByCategoryAsc(id).stream()
                .map(ProjectCategoryBudget::getCategory)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .forEach(allowedCategories::add);
        budgetItemRepository.findByProjectIdOrderByCategoryAscItemNameAsc(id).stream()
                .map(ProjectCategoryBudgetItem::getCategory)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .forEach(allowedCategories::add);
        getExpensesForProject(project).stream()
                .map(Expense::getCategory)
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .forEach(allowedCategories::add);
        if (!allowedCategories.contains(categoryName.toLowerCase(java.util.Locale.ROOT))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Choose a valid category.");
            return budgetPage;
        }

        List<String> names = itemName == null ? List.of() : itemName;
        List<String> amounts = itemAmount == null ? List.of() : itemAmount;
        if (names.size() != amounts.size()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Budget items were incomplete. Please try again.");
            return budgetPage;
        }
        List<ProjectCategoryBudgetItem> items = new java.util.ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index) == null ? "" : names.get(index).trim();
            String amountText = amounts.get(index) == null ? "" : amounts.get(index).trim();
            if (name.isEmpty() && amountText.isEmpty()) {
                continue;
            }
            if (name.isEmpty() || name.length() > 120 || amountText.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Each budget item needs a name and a valid amount. Item names must be 120 characters or fewer.");
                return budgetPage;
            }
            try {
                BigDecimal itemValue = new BigDecimal(amountText);
                if (itemValue.signum() <= 0 || itemValue.scale() > 2 || itemValue.precision() > 14
                        || itemValue.precision() - itemValue.scale() > 12) {
                    throw new NumberFormatException();
                }
                ProjectCategoryBudgetItem item = new ProjectCategoryBudgetItem();
                item.setProjectId(id);
                item.setCategory(categoryName);
                item.setItemName(name);
                item.setAmount(itemValue);
                items.add(item);
                totalBudget = totalBudget.add(itemValue);
            } catch (NumberFormatException exception) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Enter a valid item amount greater than zero with up to two decimal places.");
                return budgetPage;
            }
        }
        if (totalBudget.precision() > 14 || totalBudget.precision() - totalBudget.scale() > 12) {
            redirectAttributes.addFlashAttribute("errorMessage", "The category budget total is too large.");
            return budgetPage;
        }

        budgetItemRepository.deleteByProjectIdAndCategoryIgnoreCase(id, categoryName);
        if (items.isEmpty()) {
            categoryBudgetRepository.deleteByProjectIdAndCategoryIgnoreCase(id, categoryName);
        } else {
            budgetItemRepository.saveAll(items);
            ProjectCategoryBudget budget = categoryBudgetRepository.findByProjectIdOrderByCategoryAsc(id).stream()
                    .filter(existing -> existing.getCategory().equalsIgnoreCase(categoryName))
                    .findFirst()
                    .orElseGet(ProjectCategoryBudget::new);
            budget.setProjectId(id);
            budget.setCategory(categoryName);
            budget.setAmount(totalBudget);
            budget.setJustification(null);
            categoryBudgetRepository.save(budget);
        }
        redirectAttributes.addFlashAttribute("successMessage",
                items.isEmpty() ? "Category budget breakdown removed." : "Category budget breakdown saved.");
        return budgetPage;
    }

    @PostMapping("/projects/{id}/contributors")
    public String addContributor(@PathVariable Long id,
                                 @RequestParam String email,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !ownerSubject.equals(project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can add contributors.");
            return "redirect:/";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Closed projects are read-only. Reopen the project first.");
            return "redirect:/projects";
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!CONTRIBUTOR_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Enter a valid contributor email address.");
            return "redirect:/";
        }
        boolean alreadyAdded = contributorRepository.findByProjectIdOrderByNameAsc(id).stream()
                .anyMatch(existing -> normalizedEmail.equalsIgnoreCase(existing.getEmail()));
        if (alreadyAdded) {
            redirectAttributes.addFlashAttribute("errorMessage", "That email is already a project contributor.");
            return "redirect:/";
        }

        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        contributor.setName(normalizedEmail);
        contributor.setEmail(normalizedEmail);
        contributor.setAddedAt(LocalDate.now());
        contributor.setCanViewExpenses(true);
        contributor.setCanManageExpenses(false);
        contributorRepository.save(contributor);
        redirectAttributes.addFlashAttribute("successMessage", "Contributor added successfully.");
        return "redirect:/";
    }

    @PostMapping("/projects/{id}/contributors/{contributorId}/remove")
    public String removeContributor(@PathVariable Long id,
                                    @PathVariable Long contributorId,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !Objects.equals(ownerSubject, project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can remove contributors.");
            return "redirect:/";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Closed projects are read-only. Reopen the project first.");
            return "redirect:/projects";
        }

        ProjectContributor contributor = contributorRepository.findById(contributorId).orElse(null);
        if (contributor == null || contributor.getProject() == null
                || !id.equals(contributor.getProject().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Contributor not found in this project.");
            return "redirect:/";
        }

        contributorRepository.delete(contributor);
        redirectAttributes.addFlashAttribute("successMessage", "Contributor removed successfully.");
        return "redirect:/";
    }

    @PostMapping("/projects/{id}/contributors/{contributorId}/permission")
    public String updateContributorPermission(@PathVariable Long id,
                                              @PathVariable Long contributorId,
                                              @RequestParam(defaultValue = "false") boolean canViewExpenses,
                                              @RequestParam(defaultValue = "false") boolean canManageExpenses,
                                              Authentication authentication,
                                              RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !Objects.equals(ownerSubject, project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can change contributor access.");
            return "redirect:/projects";
        }

        ProjectContributor contributor = contributorRepository.findById(contributorId).orElse(null);
        if (contributor == null || contributor.getProject() == null
                || !id.equals(contributor.getProject().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Contributor not found in this project.");
            return "redirect:/projects";
        }

        contributor.setCanViewExpenses(canViewExpenses);
        contributor.setCanManageExpenses(canManageExpenses);
        contributorRepository.save(contributor);
        redirectAttributes.addFlashAttribute("successMessage", "Contributor access updated.");
        return "redirect:/projects";
    }

    @PostMapping("/projects/{id}/rules/expense-shares")
    @Transactional
    public String updateProjectExpenseShares(@PathVariable Long id,
                                             @RequestParam Map<String, String> parameters,
                                             Authentication authentication,
                                             RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !Objects.equals(ownerSubject, project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can change split rules.");
            return "redirect:/projects";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Closed projects are read-only. Reopen the project first.");
            return "redirect:/projects";
        }

        List<ProjectContributor> contributors = contributorRepository.findByProjectIdOrderByNameAsc(id);
        int ownerPercent;
        Map<ProjectContributor, Integer> shares = new LinkedHashMap<>();
        try {
            ownerPercent = Integer.parseInt(parameters.getOrDefault("ownerSharePercent", ""));
            if (ownerPercent < 0 || ownerPercent > 100) throw new NumberFormatException();
            int total = ownerPercent;
            for (ProjectContributor contributor : contributors) {
                String value = parameters.get("share_" + contributor.getId());
                int percent = Integer.parseInt(value == null ? "" : value);
                if (percent < 0 || percent > 100) throw new NumberFormatException();
                shares.put(contributor, percent);
                total += percent;
            }
            if (total != 100) {
                redirectAttributes.addFlashAttribute("errorMessage", "Expense shares must add up to 100%.");
                return "redirect:/projects";
            }
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Enter a whole-number share from 0% to 100% for everyone.");
            return "redirect:/projects";
        }

        project.setOwnerExpenseSharePercent(ownerPercent);
        projectRepository.save(project);
        shares.forEach(ProjectContributor::setExpenseSharePercent);
        contributorRepository.saveAll(shares.keySet());
        redirectAttributes.addFlashAttribute("successMessage", "Project expense split rule saved.");
        return "redirect:/projects";
    }

    @PostMapping("/expenses")
    public String addExpense(@Valid @ModelAttribute("expense") Expense expense,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model,
                             Authentication authentication,
                             @RequestParam(defaultValue = "false") boolean fromProject) {
        if (expense.getProjectName() == null || expense.getProjectName().isBlank()) {
            bindingResult.rejectValue("projectName", "project.required", "Project is required");
        } else {
            expense.setProjectName(expense.getProjectName().trim());
        }
        String ownerSubject = getOwnerSubject(authentication);
        if (expense.getCategory() == null || !getCategoryNames(ownerSubject).contains(expense.getCategory())) {
            bindingResult.rejectValue("category", "category.invalid", "Choose a valid category");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("username", getUsername(authentication));
            if (fromProject) {
                List<Project> savedProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
                model.addAttribute("project", new Project());
                model.addAttribute("projects", savedProjects);
                model.addAttribute("projectNames",
                        getWritableProjectNames(savedProjects, ownerSubject, getUserEmail(authentication)));
                model.addAttribute("categories", getCategoryNames(ownerSubject));
                model.addAttribute("budgetCategories", getBudgetCategoryNames(ownerSubject, savedProjects));
                model.addAttribute("projectCards", projectCards(ownerSubject, getUserEmail(authentication)));
                model.addAttribute("currentOwnerSubject", ownerSubject);
                model.addAttribute("openProjectExpenseModal", true);
                model.addAttribute("ownerImageUrl", getUserImage(authentication));
                return "projects";
            }
            List<Project> visibleProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
            List<Expense> expenses = getVisibleExpenses(ownerSubject, getUserEmail(authentication), visibleProjects);
            List<String> projects = getProjectNames(ownerSubject, expenses);
            model.addAttribute("expenses", expenses);
            model.addAttribute("userProfileImages", getExpenseSubmitterImages(expenses));
            model.addAttribute("editableExpenseIds",
                    getEditableExpenseIds(expenses, ownerSubject, getUserEmail(authentication), visibleProjects));
            model.addAttribute("projects", projects);
            model.addAttribute("projectNames",
                    getWritableProjectNames(visibleProjects, ownerSubject, getUserEmail(authentication)));
            model.addAttribute("categories", getCategoryNames(ownerSubject));
            model.addAttribute("selectedProject", "");
            model.addAttribute("total", expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            model.addAttribute("openExpenseModal", true);
            return "expenses";
        }

        Project project = projectRepository.findAll().stream()
                .filter(item -> item.getName() != null
                        && item.getName().equalsIgnoreCase(expense.getProjectName()))
                .filter(item -> isProjectAccessible(item, ownerSubject, getUserEmail(authentication)))
                .findFirst()
                .orElse(null);
        if (project == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "You do not have access to this project.");
            return "redirect:/expenses";
        }
        if (project.isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "This project is closed and cannot accept new expenses.");
            return fromProject ? "redirect:/projects" : "redirect:/expenses";
        }
        if (!hasExpenseWriteAccess(project, ownerSubject, getUserEmail(authentication))) {
            redirectAttributes.addFlashAttribute("errorMessage", "You have view-only access to this project.");
            return fromProject ? "redirect:/projects" : "redirect:/expenses";
        }
        expense.setProjectName(project.getName());
        expense.setProjectId(project.getId());
        expense.setOwnerSubject(ownerSubject);
        expense.setAddedByName(getUsername(authentication));
        expenseRepository.save(expense);
        redirectAttributes.addFlashAttribute("successMessage", "Expense added successfully.");
        if (fromProject) {
            return "redirect:/";
        }
        return "redirect:/expenses";
    }

    @PostMapping("/expenses/{id}/edit")
    public String editExpense(@PathVariable Long id,
                              @Valid @ModelAttribute("updatedExpense") ExpenseEditForm updatedExpense,
                              BindingResult bindingResult,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Expense existingExpense = expenseRepository.findById(id).orElse(null);
        if (existingExpense == null || !canManageExpense(existingExpense, ownerSubject, getUserEmail(authentication))) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Expense not found or you do not have permission to change it.");
            return "redirect:/expenses";
        }

        String category = updatedExpense.getCategory() == null ? "" : updatedExpense.getCategory().trim();
        if (bindingResult.hasErrors()
                || !getCategoryNames(ownerSubject).contains(category)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Enter valid expense details and choose a valid category.");
            return redirectToProjectExpenses(existingExpense, redirectAttributes);
        }

        existingExpense.setExpenseDate(updatedExpense.getExpenseDate());
        existingExpense.setAmount(updatedExpense.getAmount());
        existingExpense.setParticulars(updatedExpense.getParticulars().trim());
        existingExpense.setCategory(category);
        expenseRepository.save(existingExpense);
        redirectAttributes.addFlashAttribute("successMessage", "Expense updated successfully.");
        return redirectToProjectExpenses(existingExpense, redirectAttributes);
    }

    @PostMapping("/expenses/{id}/delete")
    public String deleteExpense(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Expense expense = expenseRepository.findById(id).orElse(null);
        if (expense == null || !canManageExpense(expense, ownerSubject, getUserEmail(authentication))) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Expense not found or you do not have permission to change it.");
            return "redirect:/expenses";
        }

        expenseRepository.delete(expense);
        redirectAttributes.addFlashAttribute("successMessage", "Expense deleted successfully.");
        return redirectToProjectExpenses(expense, redirectAttributes);
    }

    private List<String> getCategoryNames(String ownerSubject) {
        return java.util.stream.Stream.concat(
                        DEFAULT_CATEGORIES.stream(),
                        categoryRepository.findByOwnerSubjectOrderByNameAsc(ownerSubject).stream()
                                .map(ExpenseCategory::getName))
                .toList();
    }

    private List<String> getBudgetCategoryNames(String ownerSubject, List<Project> projects) {
        java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(getCategoryNames(ownerSubject));
        projects.stream()
                .filter(project -> project.getId() != null)
                .flatMap(project -> categoryBudgetRepository.findByProjectIdOrderByCategoryAsc(project.getId()).stream())
                .map(ProjectCategoryBudget::getCategory)
                .forEach(names::add);
        return List.copyOf(names);
    }

    private String getUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object name = user.getAttribute("name");
            if (name != null) {
                return name.toString();
            }
            Object email = user.getAttribute("email");
            if (email != null) {
                return email.toString();
            }
        }
        return "User";
    }

    private String getOwnerSubject(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object subject = user.getAttribute("sub");
            if (subject != null) {
                return subject.toString();
            }
            Object email = user.getAttribute("email");
            if (email != null) {
                return email.toString();
            }
        }
        return "";
    }

    private String getUserEmail(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object email = user.getAttribute("email");
            if (email != null) {
                return email.toString();
            }
        }
        return "";
    }

    private boolean isProjectAccessible(Project project, String ownerSubject, String email) {
        if (project == null) {
            return false;
        }
        if (ownerSubject.equals(project.getOwnerSubject())) {
            return true;
        }
        if (email == null || email.isBlank()) {
            return false;
        }
        return contributorRepository.findByEmailIgnoreCase(email).stream()
                .anyMatch(contributor -> contributor.getProject() != null
                        && project.getId().equals(contributor.getProject().getId())
                        && contributor.isCanViewExpenses());
    }

    private List<String> getWritableProjectNames(List<Project> projects, String ownerSubject, String email) {
        return projects.stream()
                .filter(project -> !project.isClosed())
                .filter(project -> hasExpenseWriteAccess(project, ownerSubject, email))
                .map(Project::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean hasExpenseWriteAccess(Project project, String ownerSubject, String email) {
        if (project == null || project.isClosed()) {
            return false;
        }
        if (Objects.equals(ownerSubject, project.getOwnerSubject())) {
            return true;
        }
        if (email == null || email.isBlank()) {
            return false;
        }
        return contributorRepository.findByEmailIgnoreCase(email).stream()
                .anyMatch(contributor -> contributor.getProject() != null
                        && Objects.equals(project.getId(), contributor.getProject().getId())
                        && contributor.isCanViewExpenses()
                        && contributor.isCanManageExpenses());
    }

    private boolean canManageExpense(Expense expense, String ownerSubject, String email) {
        Project project = null;
        if (expense.getProjectId() != null) {
            project = projectRepository.findById(expense.getProjectId()).orElse(null);
        } else if (expense.getProjectName() != null && !expense.getProjectName().isBlank()) {
            project = projectRepository.findAll().stream()
                    .filter(item -> expense.getProjectName().equalsIgnoreCase(item.getName()))
                    .findFirst()
                    .orElse(null);
        }

        if (project == null) {
            return Objects.equals(ownerSubject, expense.getOwnerSubject());
        }
        if (project.isClosed()) {
            return false;
        }
        if (Objects.equals(ownerSubject, project.getOwnerSubject())) {
            return true;
        }
        return Objects.equals(ownerSubject, expense.getOwnerSubject())
                && hasExpenseWriteAccess(project, ownerSubject, email);
    }

    private Set<Long> getEditableExpenseIds(List<Expense> expenses,
                                            String ownerSubject,
                                            String email,
                                            List<Project> visibleProjects) {
        Map<Long, Project> projectsById = new LinkedHashMap<>();
        Map<String, Project> projectsByName = new LinkedHashMap<>();
        visibleProjects.forEach(project -> {
            projectsById.put(project.getId(), project);
            if (project.getName() != null) {
                projectsByName.put(project.getName().toLowerCase(java.util.Locale.ROOT), project);
            }
        });

        Set<Long> editableIds = new LinkedHashSet<>();
        for (Expense expense : expenses) {
            if (expense.getId() == null) {
                continue;
            }
            Project project = expense.getProjectId() == null
                    ? null
                    : projectsById.get(expense.getProjectId());
            if (project == null && expense.getProjectId() == null && expense.getProjectName() != null) {
                project = projectsByName.get(expense.getProjectName().toLowerCase(java.util.Locale.ROOT));
            }
            boolean projectOpen = project == null || !project.isClosed();
            boolean projectOwner = projectOpen && project != null
                    && Objects.equals(ownerSubject, project.getOwnerSubject());
            boolean ownEntry = Objects.equals(ownerSubject, expense.getOwnerSubject());
            if (projectOpen && (projectOwner || (project != null && ownEntry
                    && hasExpenseWriteAccess(project, ownerSubject, email))
                    || (project == null && expense.getProjectId() == null
                    && (expense.getProjectName() == null || expense.getProjectName().isBlank()) && ownEntry))) {
                editableIds.add(expense.getId());
            }
        }
        return editableIds;
    }

    private String redirectToProjectExpenses(Expense expense, RedirectAttributes redirectAttributes) {
        if (expense.getProjectName() != null && !expense.getProjectName().isBlank()) {
            redirectAttributes.addAttribute("project", expense.getProjectName());
        }
        return "redirect:/expenses";
    }

    public static class ExpenseEditForm {
        @NotNull(message = "Expense date is required")
        private LocalDate expenseDate;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Enter a valid amount")
        private BigDecimal amount;

        @NotBlank(message = "Particulars are required")
        private String particulars;

        @NotBlank(message = "Category is required")
        private String category;

        public LocalDate getExpenseDate() {
            return expenseDate;
        }

        public void setExpenseDate(LocalDate expenseDate) {
            this.expenseDate = expenseDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getParticulars() {
            return particulars;
        }

        public void setParticulars(String particulars) {
            this.particulars = particulars;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }

    private List<Project> getVisibleProjects(String ownerSubject, String email) {
        LinkedHashMap<Long, Project> visible = new LinkedHashMap<>();
        projectRepository.findAll().forEach(project -> {
            if (isProjectAccessible(project, ownerSubject, email)) {
                visible.put(project.getId(), project);
            }
        });
        return visible.values().stream()
                .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<Expense> getVisibleExpenses(String ownerSubject, String email, List<Project> visibleProjects) {
        LinkedHashMap<Long, Expense> visible = new LinkedHashMap<>();
        Set<Long> visibleProjectIds = new LinkedHashSet<>();
        Set<String> visibleProjectNames = new LinkedHashSet<>();
        visibleProjects.forEach(project -> {
            if (project.getId() != null) {
                visibleProjectIds.add(project.getId());
            }
            if (project.getName() != null) {
                visibleProjectNames.add(project.getName().toLowerCase(java.util.Locale.ROOT));
            }
            getExpensesForProject(project).forEach(expense -> visible.put(expense.getId(), expense));
        });
        expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc(ownerSubject)
                .forEach(expense -> {
                    boolean inVisibleProject = expense.getProjectId() != null
                            ? visibleProjectIds.contains(expense.getProjectId())
                            : expense.getProjectName() == null || expense.getProjectName().isBlank()
                            || visibleProjectNames.contains(
                                    expense.getProjectName().toLowerCase(java.util.Locale.ROOT));
                    if (inVisibleProject) {
                        visible.putIfAbsent(expense.getId(), expense);
                    }
                });
        return visible.values().stream()
                .sorted((left, right) -> {
                    int dates = right.getExpenseDate().compareTo(left.getExpenseDate());
                    return dates != 0 ? dates : Long.compare(right.getId(), left.getId());
                })
                .toList();
    }

    private List<Expense> getExpensesForProject(Project project) {
        LinkedHashMap<Long, Expense> expenses = new LinkedHashMap<>();
        if (project.getId() != null) {
            expenseRepository.findByProjectIdOrderByExpenseDateDescIdDesc(project.getId())
                    .forEach(expense -> expenses.put(expense.getId(), expense));
        }
        if (project.getName() != null) {
            expenseRepository.findByProjectNameOrderByExpenseDateDescIdDesc(project.getName())
                    .forEach(expense -> expenses.put(expense.getId(), expense));
        }
        return expenses.values().stream()
                .sorted((left, right) -> {
                    int dates = right.getExpenseDate().compareTo(left.getExpenseDate());
                    return dates != 0 ? dates : Long.compare(right.getId(), left.getId());
                })
                .toList();
    }

    private String getUserImage(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object picture = user.getAttribute("picture");
            if (picture != null) {
                return picture.toString();
            }
        }
        return "";
    }

    private UserProfile synchronizeUserProfile(Authentication authentication) {
        String subject = getOAuthAttribute(authentication, "sub");
        if (subject == null || subject.isBlank()) {
            return null;
        }

        UserProfile profile = userProfileRepository.findById(subject).orElseGet(UserProfile::new);
        boolean changed = profile.getSubject() == null;
        profile.setSubject(subject);

        String displayName = getOAuthAttribute(authentication, "name");
        if (displayName != null && !displayName.isBlank()
                && !Objects.equals(profile.getDisplayName(), displayName)) {
            profile.setDisplayName(displayName);
            changed = true;
        }

        String email = getOAuthAttribute(authentication, "email");
        if (email != null && !email.isBlank() && !Objects.equals(profile.getEmail(), email)) {
            profile.setEmail(email);
            changed = true;
        }

        String imageUrl = getOAuthAttribute(authentication, "picture");
        if (imageUrl != null && !imageUrl.isBlank() && !Objects.equals(profile.getImageUrl(), imageUrl)) {
            profile.setImageUrl(imageUrl);
            changed = true;
        }

        if (changed) {
            userProfileRepository.save(profile);
        }
        return profile;
    }

    private String getOAuthAttribute(Authentication authentication, String name) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object value = user.getAttribute(name);
            return value == null ? null : value.toString();
        }
        return null;
    }

    private Map<String, String> getExpenseSubmitterImages(List<Expense> expenses) {
        List<String> subjects = expenses.stream()
                .map(Expense::getOwnerSubject)
                .filter(subject -> subject != null && !subject.isBlank())
                .distinct()
                .toList();
        if (subjects.isEmpty()) {
            return Map.of();
        }

        Map<String, String> imageUrls = new LinkedHashMap<>();
        userProfileRepository.findAllById(subjects).stream()
                .filter(profile -> profile.getImageUrl() != null && !profile.getImageUrl().isBlank())
                .forEach(profile -> imageUrls.put(profile.getSubject(), profile.getImageUrl()));
        return imageUrls;
    }

    private List<String> getProjectNames(String ownerSubject, List<Expense> expenses) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        projectRepository.findByOwnerSubjectOrderByNameAsc(ownerSubject).stream()
                .map(Project::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        expenses.stream()
                .map(Expense::getProjectName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        return names.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private List<Map<String, Object>> projectCards(String ownerSubject, String userEmail) {
        List<Project> savedProjects = getVisibleProjects(ownerSubject, userEmail);
        List<Expense> expenses = getVisibleExpenses(ownerSubject, userEmail, savedProjects);
        Map<String, Map<String, Object>> cardsByName = new LinkedHashMap<>();
        Map<String, UserProfile> userProfiles = new LinkedHashMap<>();
        List<String> profileSubjects = java.util.stream.Stream.concat(
                        savedProjects.stream().map(Project::getOwnerSubject),
                        expenses.stream().map(Expense::getOwnerSubject))
                .filter(subject -> subject != null && !subject.isBlank())
                .distinct()
                .toList();
        userProfileRepository.findAllById(profileSubjects)
                .forEach(profile -> userProfiles.put(profile.getSubject(), profile));
        Map<Long, List<ProjectContributor>> contributorsByProjectId = new LinkedHashMap<>();
        savedProjects.stream()
                .filter(project -> project.getId() != null)
                .forEach(project -> contributorsByProjectId.put(
                        project.getId(),
                        contributorRepository.findByProjectIdOrderByNameAsc(project.getId())));
        List<String> contributorEmails = contributorsByProjectId.values().stream()
                .flatMap(List::stream)
                .map(ProjectContributor::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        Map<String, UserProfile> userProfilesByEmail = new LinkedHashMap<>();
        if (!contributorEmails.isEmpty()) {
            userProfileRepository.findByEmailIgnoreCaseIn(contributorEmails).stream()
                    .filter(profile -> profile.getEmail() != null)
                    .forEach(profile -> userProfilesByEmail.put(
                            profile.getEmail().trim().toLowerCase(java.util.Locale.ROOT), profile));
        }

        for (Project project : savedProjects) {
            cardsByName.put(project.getName(), new LinkedHashMap<>());
        }
        for (Expense expense : expenses) {
            if (expense.getProjectName() == null || expense.getProjectName().isBlank()) {
                continue;
            }
            Map<String, Object> card = cardsByName.computeIfAbsent(expense.getProjectName(), name -> new LinkedHashMap<>());
            BigDecimal total = (BigDecimal) card.getOrDefault("total", BigDecimal.ZERO);
            card.put("total", total.add(expense.getAmount()));
            Integer count = (Integer) card.getOrDefault("count", 0);
            card.put("count", count + 1);
        }

        return cardsByName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    Map<String, Object> card = entry.getValue();
                    card.put("name", entry.getKey());
                    card.putIfAbsent("total", BigDecimal.ZERO);
                    card.putIfAbsent("count", 0);
                    card.putIfAbsent("closed", false);
                    Project savedProject = savedProjects.stream()
                            .filter(project -> entry.getKey().equals(project.getName()))
                            .findFirst()
                            .orElse(null);
                    if (savedProject != null) {
                        card.put("id", savedProject.getId());
                        card.put("ownerSubject", savedProject.getOwnerSubject());
                        card.put("closed", savedProject.isClosed());
                        card.put("canAddExpense",
                                hasExpenseWriteAccess(savedProject, ownerSubject, userEmail));
                        UserProfile ownerProfile = userProfiles.get(savedProject.getOwnerSubject());
                        String ownerName = ownerProfile == null ? null : ownerProfile.getDisplayName();
                        card.put("ownerName", ownerName == null || ownerName.isBlank()
                                ? getUsernamePlaceholder()
                                : ownerName);
                        String ownerImageUrl = ownerProfile == null ? null : ownerProfile.getImageUrl();
                        card.put("ownerImageUrl", ownerImageUrl == null ? "" : ownerImageUrl);
                        List<ProjectContributor> contributors = contributorsByProjectId.getOrDefault(
                                savedProject.getId(), List.of());
                        card.put("contributors", contributors);
                        Map<Long, String> contributorImageUrls = new LinkedHashMap<>();
                        Map<Long, String> contributorDisplayNames = new LinkedHashMap<>();
                        contributors.forEach(contributor -> {
                            String email = contributor.getEmail();
                            UserProfile contributorProfile = email == null
                                    ? null
                                    : userProfilesByEmail.get(email.trim().toLowerCase(java.util.Locale.ROOT));
                            String imageUrl = contributorProfile == null ? null : contributorProfile.getImageUrl();
                            String displayName = contributorProfile == null
                                    ? null
                                    : contributorProfile.getDisplayName();
                            if (imageUrl == null || imageUrl.isBlank()) {
                                imageUrl = contributor.getImageUrl();
                            }
                            if (displayName == null || displayName.isBlank()) {
                                displayName = contributor.getName();
                            }
                            if (displayName == null || displayName.isBlank()) {
                                displayName = email;
                            }
                            if (contributor.getId() != null) {
                                contributorDisplayNames.put(contributor.getId(), displayName);
                                if (imageUrl != null && !imageUrl.isBlank()) {
                                    contributorImageUrls.put(contributor.getId(), imageUrl);
                                }
                            }
                        });
                        card.put("contributorImageUrls", contributorImageUrls);
                        card.put("contributorDisplayNames", contributorDisplayNames);
                        List<Expense> projectExpenses = expenses.stream()
                                .filter(expense -> savedProject.getId().equals(expense.getProjectId())
                                        || (expense.getProjectId() == null
                                        && savedProject.getName().equals(expense.getProjectName())))
                                .toList();
                        BigDecimal projectTotal = projectExpenses.stream()
                                .map(Expense::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        card.put("total", projectTotal);
                        card.put("count", projectExpenses.size());
                        BigDecimal averageExpense = projectExpenses.isEmpty()
                                ? BigDecimal.ZERO
                                : projectTotal.divide(
                                        BigDecimal.valueOf(projectExpenses.size()),
                                        2,
                                        java.math.RoundingMode.HALF_UP);
                        card.put("averageExpense", averageExpense);
                        Map<String, Map<String, Object>> contributions = new LinkedHashMap<>();
                        String projectOwnerSubject = savedProject.getOwnerSubject();
                        UserProfile accountingOwnerProfile = userProfiles.get(projectOwnerSubject);
                        String ownerDisplayName = accountingOwnerProfile == null
                                ? null
                                : accountingOwnerProfile.getDisplayName();
                        contributions.put("subject:" + projectOwnerSubject, new LinkedHashMap<>(Map.of(
                                "name", ownerDisplayName == null || ownerDisplayName.isBlank()
                                        ? getUsernamePlaceholder()
                                        : ownerDisplayName,
                                "imageUrl", accountingOwnerProfile == null || accountingOwnerProfile.getImageUrl() == null
                                        ? ""
                                        : accountingOwnerProfile.getImageUrl(),
                                "amount", BigDecimal.ZERO,
                                "count", 0)));
                        contributions.get("subject:" + projectOwnerSubject)
                                .put("sharePercent", savedProject.getOwnerExpenseSharePercent());
                        contributors.forEach(contributor -> {
                            String email = contributor.getEmail();
                            UserProfile contributorProfile = email == null
                                    ? null
                                    : userProfilesByEmail.get(email.trim().toLowerCase(java.util.Locale.ROOT));
                            String subject = contributorProfile == null ? null : contributorProfile.getSubject();
                            String key = subject == null || subject.isBlank()
                                    ? "email:" + (email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT))
                                    : "subject:" + subject;
                            String contributorName = contributor.getId() == null
                                    ? null
                                    : contributorDisplayNames.get(contributor.getId());
                            String contributorImage = contributor.getId() == null
                                    ? null
                                    : contributorImageUrls.get(contributor.getId());
                            contributions.putIfAbsent(key, new LinkedHashMap<>(Map.of(
                                    "name", contributorName == null ? "Contributor" : contributorName,
                                    "imageUrl", contributorImage == null ? "" : contributorImage,
                                    "amount", BigDecimal.ZERO,
                                    "count", 0)));
                            Map<String, Object> participant = contributions.get(key);
                            participant.put("sharePercent", (Integer) participant.getOrDefault("sharePercent", 0)
                                    + contributor.getExpenseSharePercent());
                        });
                        projectExpenses.forEach(expense -> {
                            String subject = expense.getOwnerSubject();
                            UserProfile profile = subject == null ? null : userProfiles.get(subject);
                            String key = subject == null || subject.isBlank()
                                    ? "legacy:" + (expense.getAddedByName() == null
                                            ? ""
                                            : expense.getAddedByName().trim().toLowerCase(java.util.Locale.ROOT))
                                    : "subject:" + subject;
                            Map<String, Object> contribution = contributions.computeIfAbsent(key, ignored -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                String displayName = profile == null ? null : profile.getDisplayName();
                                if (displayName == null || displayName.isBlank()) {
                                    displayName = expense.getAddedByName();
                                }
                                row.put("name", displayName == null || displayName.isBlank()
                                        ? "Unknown contributor"
                                        : displayName);
                                row.put("imageUrl", profile == null || profile.getImageUrl() == null
                                        ? ""
                                        : profile.getImageUrl());
                                row.put("amount", BigDecimal.ZERO);
                                row.put("count", 0);
                                row.put("sharePercent", 0);
                                return row;
                            });
                            contribution.put("amount",
                                    ((BigDecimal) contribution.get("amount")).add(expense.getAmount()));
                            contribution.put("count", (Integer) contribution.get("count") + 1);
                        });
                        List<Map<String, Object>> accountingContributors = contributions.values().stream()
                                .peek(contribution -> {
                                    BigDecimal amount = (BigDecimal) contribution.get("amount");
                                    BigDecimal share = projectTotal.signum() == 0
                                            ? BigDecimal.ZERO
                                            : amount.multiply(BigDecimal.valueOf(100))
                                                    .divide(projectTotal, 1, java.math.RoundingMode.HALF_UP);
                                    contribution.put("share", share);
                                })
                                .sorted((left, right) -> ((String) left.get("name"))
                                        .compareToIgnoreCase((String) right.get("name")))
                                .toList();
                        card.put("accountingContributors", accountingContributors);
                        Map<String, BigDecimal> spentByCategory = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                        projectExpenses.stream()
                                .filter(expense -> expense.getCategory() != null)
                                .forEach(expense -> spentByCategory.merge(
                                        expense.getCategory(), expense.getAmount(), BigDecimal::add));
                        List<ProjectCategoryBudget> categoryBudgets =
                                categoryBudgetRepository.findByProjectIdOrderByCategoryAsc(savedProject.getId());
                        BigDecimal budgetTotal = categoryBudgets.stream()
                                .map(ProjectCategoryBudget::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        List<Map<String, Object>> budgetLines = categoryBudgets.stream()
                                .map(budget -> {
                                    BigDecimal spent = spentByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
                                    List<ProjectCategoryBudgetItem> items =
                                            budgetItemRepository.findByProjectIdAndCategoryIgnoreCaseOrderByItemNameAsc(
                                                    savedProject.getId(), budget.getCategory());
                                    Map<String, Object> line = new LinkedHashMap<>();
                                    line.put("category", budget.getCategory());
                                    line.put("amount", budget.getAmount());
                                    line.put("justification", budget.getJustification() == null ? "" : budget.getJustification());
                                    line.put("items", items);
                                    line.put("spent", spent);
                                    line.put("remaining", budget.getAmount().subtract(spent));
                                    return line;
                                })
                                .toList();
                        card.put("budgetLines", budgetLines);
                        List<Map<String, Object>> settlements = new java.util.ArrayList<>();
                        List<Map<String, Object>> debtors = new java.util.ArrayList<>();
                        List<Map<String, Object>> creditors = new java.util.ArrayList<>();
                        List<Map<String, Object>> assignedShares = accountingContributors.stream()
                                .filter(person -> ((Integer) person.getOrDefault("sharePercent", 0)) > 0)
                                .map(person -> {
                                    BigDecimal exact = projectTotal.multiply(BigDecimal.valueOf(
                                            (Integer) person.getOrDefault("sharePercent", 0)))
                                            .divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.DOWN);
                                    Map<String, Object> assigned = new LinkedHashMap<>();
                                    assigned.put("person", person);
                                    assigned.put("share", exact.setScale(2, java.math.RoundingMode.DOWN));
                                    assigned.put("remainder", exact.remainder(new BigDecimal("0.01")));
                                    return assigned;
                                }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                        BigDecimal assignedTotal = assignedShares.stream()
                                .map(row -> (BigDecimal) row.get("share"))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        int remainingCents = projectTotal.subtract(assignedTotal)
                                .movePointRight(2).intValue();
                        assignedShares.sort(Comparator.comparing(
                                (Map<String, Object> row) -> (BigDecimal) row.get("remainder")).reversed()
                                .thenComparing(row -> (String) ((Map<?, ?>) row.get("person")).get("name"),
                                        String.CASE_INSENSITIVE_ORDER));
                        for (int i = 0; i < remainingCents && !assignedShares.isEmpty(); i++) {
                            Map<String, Object> row = assignedShares.get(i % assignedShares.size());
                            row.put("share", ((BigDecimal) row.get("share")).add(new BigDecimal("0.01")));
                        }
                        Map<Map<String, Object>, BigDecimal> targetShares = new java.util.IdentityHashMap<>();
                        assignedShares.forEach(row -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> person = (Map<String, Object>) row.get("person");
                            targetShares.put(person, (BigDecimal) row.get("share"));
                        });
                        for (Map<String, Object> person : accountingContributors) {
                            BigDecimal personShare = targetShares.getOrDefault(person, BigDecimal.ZERO);
                            BigDecimal balance = ((BigDecimal) person.get("amount")).subtract(personShare);
                            if (balance.signum() < 0) {
                                debtors.add(new LinkedHashMap<>(Map.of("name", person.get("name"), "amount", balance.abs())));
                            } else if (balance.signum() > 0) {
                                creditors.add(new LinkedHashMap<>(Map.of("name", person.get("name"), "amount", balance)));
                            }
                        }
                        int debtorIndex = 0, creditorIndex = 0;
                        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
                            Map<String, Object> debtor = debtors.get(debtorIndex);
                            Map<String, Object> creditor = creditors.get(creditorIndex);
                            BigDecimal transfer = (BigDecimal) debtor.get("amount");
                            BigDecimal owed = (BigDecimal) creditor.get("amount");
                            BigDecimal settlement = transfer.min(owed).setScale(2, java.math.RoundingMode.HALF_UP);
                            settlements.add(Map.of("from", debtor.get("name"), "to", creditor.get("name"), "amount", settlement));
                            transfer = transfer.subtract(settlement);
                            owed = owed.subtract(settlement);
                            debtor.put("amount", transfer);
                            creditor.put("amount", owed);
                            if (transfer.signum() == 0) debtorIndex++;
                            if (owed.signum() == 0) creditorIndex++;
                        }
                        card.put("settlements", settlements);
                        card.put("equalShareDisplay", BigDecimal.ZERO);
                        card.put("ownerExpenseSharePercent", savedProject.getOwnerExpenseSharePercent());
                        card.put("budgetTotal", categoryBudgets.isEmpty() ? null : budgetTotal);
                        card.put("budgetRemaining", categoryBudgets.isEmpty()
                                ? null
                                : budgetTotal.subtract(projectTotal));
                        int budgetPercent = 0;
                        if (!categoryBudgets.isEmpty() && budgetTotal.signum() > 0) {
                            budgetPercent = projectTotal.compareTo(budgetTotal) >= 0
                                    ? 100
                                    : projectTotal.multiply(BigDecimal.valueOf(100))
                                            .divide(budgetTotal, 0, java.math.RoundingMode.DOWN)
                                            .intValue();
                        }
                        card.put("budgetPercent", budgetPercent);
                    } else {
                        card.put("id", null);
                        card.put("ownerName", getUsernamePlaceholder());
                        card.put("ownerImageUrl", "");
                        card.put("averageExpense", BigDecimal.ZERO);
                        card.put("accountingContributors", List.of());
                        card.put("settlements", List.of());
                        card.put("contributors", List.of());
                        card.put("canAddExpense", false);
                        card.put("contributorImageUrls", Map.of());
                        card.put("budgetLines", List.of());
                        card.put("budgetTotal", null);
                        card.put("budgetRemaining", null);
                        card.put("budgetPercent", 0);
                    }
                    return card;
                })
                .toList();
    }

    private String getUsernamePlaceholder() {
        return "Owner";
    }
}
