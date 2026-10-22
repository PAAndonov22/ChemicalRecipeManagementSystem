package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.dto.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.models.Customer;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.repositories.CategoryRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.CustomerRepository;
import org.codingburgas.ChemicalRecipeSystem.services.PdfService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.codingburgas.ChemicalRecipeSystem.services.ReportService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Starts the whole app with an in-memory database filled by MockData. */
@SpringBootTest
class ChemicalRecipeSystemTests {
    @Autowired
    UserService userService;
    @Autowired
    ReportService reportService;
    @Autowired
    PdfService pdfService;
    @Autowired
    RecipeService recipeService;
    @Autowired
    CategoryRepository categories;
    @Autowired
    CustomerRepository customers;

    @Test
    void adminCanLogInWithDemoPassword() {
        assertTrue(userService.login("admin@crms.local", "Admin123!").isPresent());
        assertTrue(userService.login("admin@crms.local", "wrong").isEmpty());
    }

    @Test
    void deactivatedUserCannotLogIn() {
        UserDto admin = userService.login("admin@crms.local", "Admin123!").orElseThrow();
        UserDto vet = userService.create("Test Vet", "test.vet@crms.local", "0888", "Secret1", Role.VET);
        userService.setActive(vet.getId(), false, admin.getId());
        assertTrue(userService.login("test.vet@crms.local", "Secret1").isEmpty());
    }

    @Test
    void searchIsPagedAndFilteredByRole() {
        var page = userService.search("", Role.ADMIN, null, "fullName", "asc", 0, 10);
        assertFalse(page.isEmpty());
        assertTrue(page.getContent().stream().allMatch(u -> u.getRole() == Role.ADMIN));
        assertTrue(userService.search("", null, null, "email", "asc", 0, 10).getContent().size() <= 10);
    }

    @Test
    void userDtoNeverCarriesThePassword() {
        assertTrue(Arrays.stream(UserDto.class.getDeclaredFields()).noneMatch(f -> f.getName().equals("password")));
    }

    @Test
    void monthlyReportHasOneRowPerMonth() {
        PeriodReport report = reportService.monthly(6, LocalDate.now());
        assertEquals(6, report.rows().size());
        assertTrue(report.totalEvents() > 0);
    }

    @Test
    void pdfIsGenerated() {
        byte[] pdf = pdfService.create(reportService.monthly(12, LocalDate.now()));
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void demoRecipesHaveIngredientsCategoriesAndSafetySheet() {
        RecipeDto spray = recipeService.search("Antiseptic Spray").getFirst();
        assertEquals(3, spray.getIngredients().size());
        assertEquals(List.of("Disinfectant"), spray.getCategories());
        assertEquals(SignalWord.DANGER, spray.getSafety().getSignalWord());
    }

    @Test
    void recipeIsSavedWithAllItsRelations() {
        RecipeDto saved = recipeService.save(form("RX-T-001", "Test Gel", "Distilled Water, 900, ml\nGlycerin, 100, ml\nNew Thickener, 5, g", "Cosmetic"));
        assertEquals(3, saved.getIngredients().size());
        assertEquals("New Thickener", saved.getIngredients().get(2).getIngredientName()); // created on the fly
        assertEquals(List.of("Cosmetic"), saved.getCategories());
        assertEquals(SignalWord.WARNING, saved.getSafety().getSignalWord());
    }

    @Test
    void editingRecipeDeletesRemovedLines() {
        RecipeDto saved = recipeService.save(form("RX-T-002", "Test Wash", "Distilled Water, 900, ml\nEthanol, 100, ml", "Disinfectant"));
        RecipeForm edit = recipeService.getForm(saved.getId());
        edit.setIngredients("Ethanol, 1000, ml");
        edit.setCategoryIds(List.of());
        recipeService.save(edit);

        RecipeDto reloaded = recipeService.get(saved.getId());
        assertEquals(1, reloaded.getIngredients().size()); // orphanRemoval deleted the water line
        assertTrue(reloaded.getCategories().isEmpty());
    }

    @Test
    void duplicateCodeAndBadIngredientsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> recipeService.save(form("RX-1001", "Copy", "Distilled Water, 1, l", "Cosmetic")));
        assertThrows(IllegalArgumentException.class, () -> recipeService.save(form("RX-T-003", "Bad", "Ethanol, lots, ml", "Cosmetic")));
        assertThrows(IllegalArgumentException.class, () -> recipeService.save(form("RX-T-004", "Empty", "  ", "Cosmetic")));
    }

    @Test
    @Transactional
    void customerHasManyOrders() {
        Customer customer = customers.findAllByOrderByCompanyNameAsc().getFirst();
        assertFalse(customer.getOrders().isEmpty());
        assertTrue(customer.getOrders().stream().allMatch(order -> order.getCustomer() == customer));
    }

    private RecipeForm form(String code, String name, String ingredients, String category) {
        RecipeForm form = new RecipeForm();
        form.setCode(code);
        form.setName(name);
        form.setPrice(5);
        form.setIngredients(ingredients);
        form.setCategoryIds(List.of(categories.findByName(category).orElseThrow().getId()));
        form.setSignalWord(SignalWord.WARNING);
        form.setHazardClass("Eye irritation, Cat. 2");
        return form;
    }
}
