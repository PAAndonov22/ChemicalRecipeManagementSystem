package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.repositories.CategoryRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Renders every page through Spring MVC + Thymeleaf, logged in as the demo admin. */
@SpringBootTest
class WebPagesTest {
    @Autowired
    WebApplicationContext context;
    @Autowired
    UserService userService;
    @Autowired
    RecipeService recipeService;
    @Autowired
    RecipeRepository recipes;
    @Autowired
    CategoryRepository categories;

    private MockMvc mvc;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        admin = new MockHttpSession();
        admin.setAttribute("user", userService.login("admin@crms.local", "Admin123!").orElseThrow());
    }

    @Test
    void everyPageRenders() throws Exception {
        Long recipeId = recipes.findByCode("RX-1005").orElseThrow().getId();
        Long adminId = userService.login("admin@crms.local", "Admin123!").orElseThrow().getId();
        List<String> pages = List.of("/", "/recipes", "/recipes?q=spray", "/recipes/new", "/recipes/" + recipeId + "/edit",
            "/orders", "/orders?sort=total&dir=asc", "/reports", "/reports?groupBy=week", "/reports/monthly?months=6",
            "/insights", "/profile", "/admin/users", "/admin/users/new", "/admin/users/" + adminId,
            "/admin/users/registrations");
        for (String page : pages) {
            mvc.perform(get(page).session(admin)).andExpect(status().isOk());
        }
    }

    @Test
    void pdfReportIsServed() throws Exception {
        mvc.perform(get("/reports/pdf").session(admin)
                .param("from", LocalDate.now().minusMonths(3).toString())
                .param("to", LocalDate.now().toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void pagesNeedLogin() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/recipes")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void recipeFormCreatesRecipeWithRelations() throws Exception {
        String categoryId = categories.findByName("Veterinary").orElseThrow().getId().toString();
        mvc.perform(post("/recipes/save").session(admin)
                .param("code", "RX-WEB-1")
                .param("name", "Web Form Rinse")
                .param("price", "2.50")
                .param("ingredients", "Distilled Water, 950, ml\nSodium Chloride, 9, g")
                .param("categoryIds", categoryId)
                .param("hazardClass", "Not classified")
                .param("signalWord", "NONE"))
            .andExpect(redirectedUrl("/recipes"));

        RecipeDto saved = recipeService.search("Web Form Rinse").getFirst();
        assertEquals(2, saved.getIngredients().size());
        assertEquals(List.of("Veterinary"), saved.getCategories());
        assertEquals(SignalWord.NONE, saved.getSafety().getSignalWord());
    }
}
