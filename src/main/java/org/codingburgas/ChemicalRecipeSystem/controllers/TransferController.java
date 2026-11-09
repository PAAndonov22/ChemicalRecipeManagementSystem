package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.RequirePermission;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeSearch;
import org.codingburgas.ChemicalRecipeSystem.services.ExportService;
import org.codingburgas.ChemicalRecipeSystem.services.ImportService;
import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** CSV / Excel export (follows the active filters) and the two-step import. */
@Controller
public class TransferController {
    static final List<String> HEADERS = List.of("code", "name", "price", "description", "categories", "ingredients",
        "hazardClass", "signalWord", "storageConditions", "protectiveEquipment");

    private final RecipeService recipes;
    private final OrderService orders;
    private final ExportService export;
    private final ImportService importer;

    public TransferController(RecipeService recipes, OrderService orders, ExportService export, ImportService importer) {
        this.recipes = recipes;
        this.orders = orders;
        this.export = export;
        this.importer = importer;
    }

    @GetMapping("/recipes/export")
    @RequirePermission(Permission.RECIPE_READ)
    public void exportRecipes(@ModelAttribute RecipeSearch search, @RequestParam(defaultValue = "csv") String format,
                              HttpServletResponse response) throws IOException {
        export.write(format, "recipes", HEADERS, recipeRows(recipes.searchAll(search)), response);
    }

    static List<List<Object>> recipeRows(List<RecipeDto> list) {
        List<List<Object>> rows = new ArrayList<>();
        for (RecipeDto r : list) {
            var s = r.getSafety();
            rows.add(List.of(r.getCode(), r.getName(), r.getPrice(), nz(r.getDescription()), String.join(";", r.getCategories()),
                r.getIngredients().stream().map(i -> i.getIngredientName() + ":" + org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper.formatQuantity(i.getQuantity()) + ":" + i.getUnit()).collect(Collectors.joining(";")),
                s == null ? "" : nz(s.getHazardClass()), s == null ? "NONE" : s.getSignalWord().name(),
                s == null ? "" : nz(s.getStorageConditions()), s == null ? "" : nz(s.getProtectiveEquipment())));
        }
        return rows;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @GetMapping("/orders/export")
    @RequirePermission(Permission.RECIPE_READ)
    public void exportOrders(@RequestParam(required = false) String week,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) DayOfWeek day, @RequestParam(required = false) Integer hour,
                             @RequestParam(defaultValue = "date") String sort, @RequestParam(defaultValue = "desc") String dir,
                             @RequestParam(defaultValue = "csv") String format, HttpServletResponse response) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        for (var o : orders.search(week, date, day, hour, sort, dir.equals("desc"))) {
            rows.add(List.of(o.getOrderedAt().toString(), nz(o.getCustomerName()), nz(o.getRecipeName()), nz(o.getCreatedByName()), o.getQuantity(), o.getTotal()));
        }
        export.write(format, "orders", List.of("ordered at", "customer", "recipe", "entered by", "quantity", "total"), rows, response);
    }

    @GetMapping("/recipes/import/template")
    @RequirePermission(Permission.RECIPE_WRITE)
    public void template(HttpServletResponse response) throws IOException {
        export.write("csv", "recipe-import-template", HEADERS, List.of(List.of("RX-2001", "Sample Rinse", 3.5, "Example", "Cosmetic",
            "Distilled Water:950:ml;Glycerin:50:ml", "Not classified", "NONE", "Cool and dry", "Gloves")), response);
    }

    @GetMapping("/recipes/import")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String importPage(HttpSession session, Model model) {
        model.addAttribute("preview", session.getAttribute("importPreview"));
        return "recipes/import";
    }

    @PostMapping("/recipes/import/preview")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String preview(@RequestParam MultipartFile file, HttpSession session) throws IOException {
        session.setAttribute("importPreview", importer.preview(file));
        return "redirect:/recipes/import";
    }

    @PostMapping("/recipes/import/commit")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String commit(@RequestParam(defaultValue = "atomic") String mode, HttpSession session, RedirectAttributes redirect) {
        var preview = (ImportService.Preview) session.getAttribute("importPreview");
        if (preview == null) {
            throw new IllegalArgumentException("Upload a file first.");
        }
        int saved = importer.commit(preview, mode.equals("atomic"));
        session.removeAttribute("importPreview");
        redirect.addFlashAttribute("success", saved + " recipe(s) imported.");
        return "redirect:/recipes";
    }
}
