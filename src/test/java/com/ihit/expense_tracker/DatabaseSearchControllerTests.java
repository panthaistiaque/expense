package com.ihit.expense_tracker;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.ui.ExtendedModelMap;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSearchControllerTests {

    private JdbcDataSource dataSource;
    private DatabaseSearchController controller;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dbsearch" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        controller = new DatabaseSearchController(dataSource);
    }

    @Test
    void allowsOnlyTheConfiguredAccount() {
        assertTrue(DatabaseSearchAccess.isAllowed(authentication("panthaistiaque@gmail.com")));
        assertTrue(DatabaseSearchAccess.isAllowed(authentication("PANTHAISTIAQUE@GMAIL.COM")));
        assertFalse(DatabaseSearchAccess.isAllowed(authentication("someone@example.com")));
    }

    @Test
    void listsAndLoadsOnlyMetadataSelectedTablesWithCappedRows() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE test_records (id INTEGER, details VARCHAR(20))");
            for (int id = 1; id <= 101; id++) {
                statement.execute("INSERT INTO test_records VALUES (" + id + ", 'row-" + id + "')");
            }
        }
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.databaseSearch("TEST_RECORDS",
                authentication("panthaistiaque@gmail.com"), model);

        assertEquals("db-search", view);
        assertTrue(((List<?>) model.getAttribute("tableNames")).contains("TEST_RECORDS"));
        assertEquals(List.of("ID", "DETAILS"), model.getAttribute("columns"));
        assertEquals(100, ((List<?>) model.getAttribute("rows")).size());
        assertTrue((Boolean) model.getAttribute("truncated"));
    }

    @Test
    void rejectsUnlistedTablesAndUnauthorizedAccounts() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.databaseSearch("TEST_RECORDS; DROP TABLE test_records",
                authentication("panthaistiaque@gmail.com"), model);

        assertEquals("Select a table from the list.", model.getAttribute("errorMessage"));
        assertEquals(List.of(), model.getAttribute("rows"));
        assertThrows(AccessDeniedException.class, () -> controller.databaseSearch(
                null, authentication("someone@example.com"), new ExtendedModelMap()));
    }

    private UsernamePasswordAuthenticationToken authentication(String email) {
        var authority = new SimpleGrantedAuthority("ROLE_USER");
        var principal = new DefaultOAuth2User(
                List.of(authority),
                Map.of(
                        "sub", email,
                        "name", "Database Admin",
                        "email", email,
                        "picture", ""),
                "sub");
        return new UsernamePasswordAuthenticationToken(principal, "N/A", List.of(authority));
    }
}
