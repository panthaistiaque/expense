package com.ihit.expense_tracker;

import jakarta.validation.Valid;
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
import java.util.Map;

@Controller
public class ExpenseController {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Food", "Transport", "Shopping", "Utilities",
            "Health", "Education", "Entertainment", "Other");

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
        model.addAttribute("recentExpenses", expenses.stream().limit(5).toList());
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
        model.addAttribute("projects", projects);
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
        synchronizeUserProfile(authentication);
        List<Project> savedProjects = getVisibleProjects(ownerSubject, getUserEmail(authentication));
        for (Project project : savedProjects) {
            if (ownerSubject.equals(project.getOwnerSubject())) {
                if (project.getOwnerName() == null || project.getOwnerName().isBlank()) {
                    project.setOwnerName(getUsername(authentication));
                }
                if (project.getOwnerImageUrl() == null || project.getOwnerImageUrl().isBlank()) {
                    project.setOwnerImageUrl(getUserImage(authentication));
                }
            }
        }
        projectRepository.saveAll(savedProjects);
        model.addAttribute("username", getUsername(authentication));
        model.addAttribute("ownerImageUrl", getUserImage(authentication));
        model.addAttribute("project", new Project());
        Expense expense = new Expense();
        expense.setExpenseDate(LocalDate.now());
        model.addAttribute("expense", expense);
        model.addAttribute("projects", savedProjects);
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
            model.addAttribute("expense", expense);
            model.addAttribute("projects", getVisibleProjects(ownerSubject, getUserEmail(authentication)));
            model.addAttribute("categories", getCategoryNames(ownerSubject));
            model.addAttribute("budgetCategories", getBudgetCategoryNames(
                    ownerSubject, getVisibleProjects(ownerSubject, getUserEmail(authentication))));
            model.addAttribute("projectCards", projectCards(ownerSubject, getUserEmail(authentication)));
            model.addAttribute("currentOwnerSubject", ownerSubject);
            model.addAttribute("openProjectModal", true);
            return "projects";
        }

        project.setOwnerSubject(ownerSubject);
        project.setOwnerName(getUsername(authentication));
        project.setOwnerImageUrl(getUserImage(authentication));
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
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Project name is required.");
            return "redirect:/";
        }
        project.setName(name.trim());
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("successMessage", "Project updated successfully.");
        return "redirect:/";
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
                                 @RequestParam String name,
                                 @RequestParam String email,
                                 @RequestParam(required = false) String imageUrl,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String ownerSubject = getOwnerSubject(authentication);
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null || !ownerSubject.equals(project.getOwnerSubject())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the project owner can add contributors.");
            return "redirect:/";
        }
        if (name == null || name.isBlank() || email == null || email.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Contributor name and email are required.");
            return "redirect:/";
        }

        ProjectContributor contributor = new ProjectContributor();
        contributor.setProject(project);
        contributor.setName(name.trim());
        contributor.setEmail(email.trim());
        contributor.setImageUrl(imageUrl == null ? "" : imageUrl.trim());
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
                model.addAttribute("categories", getCategoryNames(ownerSubject));
                model.addAttribute("budgetCategories", getBudgetCategoryNames(ownerSubject, savedProjects));
                model.addAttribute("projectCards", projectCards(ownerSubject, getUserEmail(authentication)));
                model.addAttribute("currentOwnerSubject", ownerSubject);
                model.addAttribute("openProjectExpenseModal", true);
                model.addAttribute("ownerImageUrl", getUserImage(authentication));
                return "projects";
            }
            List<Expense> allExpenses = expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc(ownerSubject);
            List<String> projects = getProjectNames(ownerSubject, allExpenses);
            List<Expense> expenses = allExpenses;
            model.addAttribute("expenses", expenses);
            model.addAttribute("projects", projects);
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
                        && project.getId().equals(contributor.getProject().getId()));
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
        visibleProjects.forEach(project -> {
            getExpensesForProject(project).forEach(expense -> visible.put(expense.getId(), expense));
        });
        expenseRepository.findByOwnerSubjectOrderByExpenseDateDescIdDesc(ownerSubject)
                .forEach(expense -> visible.putIfAbsent(expense.getId(), expense));
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

    private void synchronizeUserProfile(Authentication authentication) {
        String subject = getOAuthAttribute(authentication, "sub");
        if (subject == null || subject.isBlank()) {
            return;
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
    }

    private String getOAuthAttribute(Authentication authentication, String name) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object value = user.getAttribute(name);
            return value == null ? null : value.toString();
        }
        return null;
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
        List<String> ownerSubjects = savedProjects.stream()
                .map(Project::getOwnerSubject)
                .filter(subject -> subject != null && !subject.isBlank())
                .distinct()
                .toList();
        userProfileRepository.findAllById(ownerSubjects)
                .forEach(profile -> userProfiles.put(profile.getSubject(), profile));

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
                    Project savedProject = savedProjects.stream()
                            .filter(project -> entry.getKey().equals(project.getName()))
                            .findFirst()
                            .orElse(null);
                    if (savedProject != null) {
                        card.put("id", savedProject.getId());
                        card.put("ownerSubject", savedProject.getOwnerSubject());
                        card.put("ownerName", savedProject.getOwnerName());
                        UserProfile ownerProfile = userProfiles.get(savedProject.getOwnerSubject());
                        String profileImageUrl = ownerProfile == null ? null : ownerProfile.getImageUrl();
                        card.put("ownerImageUrl", profileImageUrl == null || profileImageUrl.isBlank()
                                ? savedProject.getOwnerImageUrl()
                                : profileImageUrl);
                        card.put("contributors", contributorRepository.findByProjectIdOrderByNameAsc(savedProject.getId()));
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
                        card.put("contributors", List.of());
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
