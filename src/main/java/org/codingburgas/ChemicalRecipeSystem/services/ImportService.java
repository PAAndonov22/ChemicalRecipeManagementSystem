package org.codingburgas.ChemicalRecipeSystem.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.models.Category;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.repositories.CategoryRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk import from CSV/XLSX in two steps: preview (nothing is saved) and commit.
 * Columns = the export columns: code, name, price, description, categories (a;b), ingredients (name:qty:unit;...),
 * hazardClass, signalWord, storageConditions, protectiveEquipment.
 */
@Service
public class ImportService {
    public record Row(int line, String code, String name, String status, String message, RecipeForm form) {
    }

    public record Preview(List<Row> rows) {
        public long count(String status) { return rows.stream().filter(r -> r.status().equals(status)).count(); }
    }

    public static final int MAX_ROWS = 500;

    private final RecipeService recipeService;
    private final RecipeRepository recipes;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final PlatformTransactionManager tx;

    public ImportService(RecipeService recipeService, RecipeRepository recipes, CategoryRepository categories, AuditService audit,
                         PlatformTransactionManager tx) {
        this.recipeService = recipeService;
        this.recipes = recipes;
        this.categories = categories;
        this.audit = audit;
        this.tx = tx;
    }

    public Preview preview(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a CSV or XLSX file first.");
        }
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase();
        List<List<String>> table = name.endsWith(".xlsx") ? readXlsx(file.getInputStream())
            : name.endsWith(".csv") ? readCsv(new String(file.getBytes(), StandardCharsets.UTF_8)) : null;
        if (table == null) {
            throw new IllegalArgumentException("Only .csv and .xlsx files can be imported.");
        }
        if (table.size() < 2) {
            throw new IllegalArgumentException("The file has no data rows.");
        }
        if (table.size() > MAX_ROWS + 1) {
            throw new IllegalArgumentException("At most " + MAX_ROWS + " rows can be imported at once.");
        }
        List<String> header = table.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        for (String required : List.of("code", "name", "price", "ingredients")) {
            if (!header.contains(required)) {
                throw new IllegalArgumentException("Missing column: " + required);
            }
        }
        Map<String, Long> categoryIds = categories.findAll().stream()
            .collect(Collectors.toMap(c -> c.getName().toLowerCase(), Category::getId, (a, b) -> a));
        Set<String> seen = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            Function<String, String> get = col -> header.indexOf(col) < 0 || header.indexOf(col) >= cells.size() ? "" : cells.get(header.indexOf(col)).trim();
            RecipeForm form = new RecipeForm();
            form.setCode(get.apply("code"));
            form.setName(get.apply("name"));
            form.setDescription(get.apply("description"));
            form.setIngredients(get.apply("ingredients").replace(";", "\n").replace(":", ", "));
            form.setHazardClass(get.apply("hazardclass"));
            form.setStorageConditions(get.apply("storageconditions"));
            form.setProtectiveEquipment(get.apply("protectiveequipment"));
            String status = "OK";
            String message = "";
            try {
                form.setPrice(Double.parseDouble(get.apply("price")));
                String signal = get.apply("signalword");
                form.setSignalWord(signal.isEmpty() ? SignalWord.NONE : SignalWord.valueOf(signal.toUpperCase()));
                List<Long> ids = new ArrayList<>();
                for (String cat : get.apply("categories").split(";")) {
                    if (!cat.isBlank()) {
                        Long id = categoryIds.get(cat.trim().toLowerCase());
                        if (id == null) {
                            throw new IllegalArgumentException("Unknown category: " + cat.trim());
                        }
                        ids.add(id);
                    }
                }
                form.setCategoryIds(ids);
                recipeService.check(form);
                if (recipes.findByCode(form.getCode().trim()).isPresent() || !seen.add(form.getCode().trim().toLowerCase())) {
                    status = "DUPLICATE";
                    message = "Code " + form.getCode() + " already exists.";
                }
            } catch (NumberFormatException e) {
                status = "ERROR";
                message = "Price or number is not valid.";
            } catch (IllegalArgumentException e) {
                status = "ERROR";
                message = e.getMessage() == null || e.getMessage().startsWith("No enum") ? "Signal word must be NONE, WARNING or DANGER." : e.getMessage();
            }
            rows.add(new Row(i + 1, form.getCode(), form.getName(), status, message, form));
        }
        return new Preview(rows);
    }

    /** atomic = all or nothing; otherwise the valid rows are saved and the rest skipped. Returns the saved count. */
    public int commit(Preview preview, boolean atomic) {
        List<Row> ok = preview.rows().stream().filter(r -> r.status().equals("OK")).toList();
        int saved;
        if (atomic) {
            long bad = preview.rows().size() - ok.size();
            if (bad > 0) {
                throw new IllegalArgumentException("Nothing was imported: " + bad + " row(s) have problems (all-or-nothing mode).");
            }
            new TransactionTemplate(tx).executeWithoutResult(s -> ok.forEach(r -> recipeService.save(r.form())));
            saved = ok.size();
        } else {
            saved = 0;
            for (Row r : ok) {
                recipeService.save(r.form());
                saved++;
            }
        }
        audit.log("IMPORT", "Recipe", null, saved + " imported, " + (preview.rows().size() - saved) + " skipped");
        return saved;
    }

    static List<List<String>> readXlsx(InputStream in) throws IOException {
        List<List<String>> table = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (XSSFWorkbook book = new XSSFWorkbook(in)) {
            Sheet sheet = book.getSheetAt(0);
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    cells.add(fmt.formatCellValue(row.getCell(c)));
                }
                table.add(cells);
            }
        }
        return table;
    }

    /** Small RFC 4180 parser: quotes, doubled quotes, commas/semicolons and line breaks inside quotes. */
    static List<List<String>> readCsv(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char sep = text.indexOf(';') >= 0 && text.indexOf(';') < (text.indexOf('\n') < 0 ? text.length() : text.indexOf('\n'))
            && (text.indexOf(',') < 0 || text.indexOf(';') < text.indexOf(',')) ? ';' : ',';
        List<List<String>> table = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) {
                    table.add(row);
                }
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            table.add(row);
        }
        return table;
    }
}
