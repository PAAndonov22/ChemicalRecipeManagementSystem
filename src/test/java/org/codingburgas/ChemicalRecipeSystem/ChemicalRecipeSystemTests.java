package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.codingburgas.ChemicalRecipeSystem.models.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.services.PdfService;
import org.codingburgas.ChemicalRecipeSystem.services.ReportService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the whole app with an in-memory database filled by MockData. */
@SpringBootTest
class ChemicalRecipeSystemTests {
    @Autowired
    UserService userService;
    @Autowired
    ReportService reportService;
    @Autowired
    PdfService pdfService;

    @Test
    void adminCanLogInWithDemoPassword() {
        assertTrue(userService.login("admin@crms.local", "Admin123!").isPresent());
        assertTrue(userService.login("admin@crms.local", "wrong").isEmpty());
    }

    @Test
    void deactivatedUserCannotLogIn() {
        User admin = userService.login("admin@crms.local", "Admin123!").orElseThrow();
        User vet = userService.create("Test Vet", "test.vet@crms.local", "0888", "Secret1", Role.VET);
        userService.setActive(vet.getId(), false, admin);
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
}
