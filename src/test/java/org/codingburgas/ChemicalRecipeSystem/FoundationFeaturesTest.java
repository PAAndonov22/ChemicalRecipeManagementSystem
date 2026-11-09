package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.errors.ConflictException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.services.AuditService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Permissions (403), audit log, soft delete + trash, optimistic locking, clean error pages. */
@SpringBootTest
class FoundationFeaturesTest {
    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired RecipeService recipeService;
    @Autowired RecipeRepository recipes;
    @Autowired AuditService auditService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpSession login(String email, String password) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", userService.login(email, password).orElseThrow());
        return session;
    }

    private RecipeForm form(String code) {
        RecipeForm form = new RecipeForm();
        form.setCode(code);
        form.setName("Found " + code);
        form.setPrice(1.5);
        form.setIngredients("Distilled Water, 100, ml");
        return form;
    }

    @Test
    void readOnlyRoleGets403OnDirectPostButChemistIsAllowed() throws Exception {
        MockHttpSession vet = login("vet@crms.local", "Pass123!");
        mvc.perform(post("/recipes/save").session(vet).param("code", "RX-F-1").param("name", "x").param("price", "1")
                .param("ingredients", "Water, 1, ml")).andExpect(status().isForbidden());
        mvc.perform(get("/recipes/new").session(vet)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").session(login("chemist@crms.local", "Pass123!"))).andExpect(status().isForbidden());

        mvc.perform(post("/recipes/save").session(login("chemist@crms.local", "Pass123!"))
                .param("code", "RX-F-2").param("name", "Chemist Made").param("price", "2")
                .param("ingredients", "Water, 1, ml")).andExpect(redirectedUrl("/recipes"));
    }

    @Test
    void softDeleteHidesRecipeRestoreBringsItBackAndAuditRecordsIt() {
        Long id = recipeService.save(form("RX-F-3")).getId();
        recipeService.delete(id);
        assertTrue(recipeService.search("Found RX-F-3").isEmpty());
        assertTrue(recipeService.trash().stream().anyMatch(r -> r.getId().equals(id)));
        assertThrows(NotFoundException.class, () -> recipeService.get(id));
        assertTrue(recipes.findById(id).isPresent(), "row stays in the database");

        recipeService.restore(id);
        assertEquals(1, recipeService.search("Found RX-F-3").size());
        var actions = auditService.search(null, "Recipe", null, null, 0, 100).map(l -> l.getAction() + l.getEntityId()).getContent();
        assertTrue(actions.contains("CREATE" + id) && actions.contains("DELETE" + id) && actions.contains("RESTORE" + id));
    }

    @Test
    void purgeOnlyWorksFromTrash() {
        Long id = recipeService.save(form("RX-F-4")).getId();
        assertThrows(IllegalArgumentException.class, () -> recipeService.purge(id));
        recipeService.delete(id);
        recipeService.purge(id);
        assertFalse(recipes.findById(id).isPresent());
    }

    @Test
    void secondEditorWithStaleVersionGetsConflict() {
        Long id = recipeService.save(form("RX-F-5")).getId();
        RecipeForm first = recipeService.getForm(id);
        RecipeForm second = recipeService.getForm(id);
        first.setName("Edited by first");
        recipeService.save(first);
        second.setName("Edited by second");
        assertThrows(ConflictException.class, () -> recipeService.save(second));
        assertEquals("Edited by first", recipeService.get(id).getName());
    }

    @Test
    void missingPageShowsCleanNotFoundWithoutStackTrace() throws Exception {
        mvc.perform(get("/recipes/999999/edit").session(login("admin@crms.local", "Admin123!")))
            .andExpect(status().isNotFound())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }
}
