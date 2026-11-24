package com.ihit.expense_tracker;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DatabaseSearchController {

    private static final int MAX_ROWS = 100;
    private static final int MAX_CELL_LENGTH = 500;

    private final DataSource dataSource;

    public DatabaseSearchController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/db-search")
    public String databaseSearch(@RequestParam(required = false) String table,
                                 Authentication authentication,
                                 Model model) throws SQLException {
        if (!DatabaseSearchAccess.isAllowed(authentication)) {
            throw new AccessDeniedException("Database search is restricted.");
        }

        addAccountDetails(model, authentication);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            List<String> tableNames = getTableNames(metadata, catalog, schema);
            model.addAttribute("tableNames", tableNames);
            model.addAttribute("selectedTable", "");
            model.addAttribute("columns", List.of());
            model.addAttribute("rows", List.of());
            model.addAttribute("truncated", false);

            if (table != null && !table.isBlank()) {
                if (!tableNames.contains(table)) {
                    model.addAttribute("errorMessage", "Select a table from the list.");
                } else {
                    model.addAttribute("selectedTable", table);
                    loadTableData(connection, metadata, table, model);
                }
            }
        }
        return "db-search";
    }

    private List<String> getTableNames(DatabaseMetaData metadata, String catalog, String schema)
            throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet tables = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
        }
        tableNames.sort(String.CASE_INSENSITIVE_ORDER);
        return tableNames;
    }

    private void loadTableData(Connection connection,
                               DatabaseMetaData metadata,
                               String table,
                               Model model) throws SQLException {
        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            if (!table.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
                throw new SQLException("The database driver cannot safely quote this table name.");
            }
        } else {
            table = quote + table.replace(quote, quote + quote) + quote;
        }

        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(MAX_ROWS + 1);
            try (ResultSet resultSet = statement.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData resultMetadata = resultSet.getMetaData();
                for (int index = 1; index <= resultMetadata.getColumnCount(); index++) {
                    columns.add(resultMetadata.getColumnLabel(index));
                }
                while (resultSet.next()) {
                    if (rows.size() == MAX_ROWS) {
                        truncated = true;
                        break;
                    }
                    List<String> row = new ArrayList<>();
                    for (int index = 1; index <= columns.size(); index++) {
                        row.add(formatValue(resultSet.getObject(index)));
                    }
                    rows.add(row);
                }
            }
        }
        model.addAttribute("columns", columns);
        model.addAttribute("rows", rows);
        model.addAttribute("truncated", truncated);
    }

    private String formatValue(Object value) throws SQLException {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            return "[binary data: " + bytes.length + " bytes]";
        }
        if (value instanceof Blob) {
            return "[BLOB]";
        }
        if (value instanceof Clob) {
            return "[CLOB]";
        }
        String text = value.toString();
        return text.length() <= MAX_CELL_LENGTH
                ? text
                : text.substring(0, MAX_CELL_LENGTH) + "...";
    }

    private void addAccountDetails(Model model, Authentication authentication) {
        String username = "User";
        String imageUrl = "";
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object name = user.getAttribute("name");
            if (name != null) {
                username = name.toString();
            }
            Object picture = user.getAttribute("picture");
            if (picture != null) {
                imageUrl = picture.toString();
            }
        }
        model.addAttribute("username", username);
        model.addAttribute("ownerImageUrl", imageUrl);
        model.addAttribute("userEmail", DatabaseSearchAccess.getEmail(authentication));
        model.addAttribute("databaseSearchAllowed", true);
    }
}
