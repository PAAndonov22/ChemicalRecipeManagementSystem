package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.dto.PreferencesDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeSearch;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ForbiddenException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.models.Theme;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** One positive and one negative test for each extension feature (workflow, versions, search, export, import, ...). */
@SpringBootTest
class FeatureTests {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired WebApplicationContext context;
    @Autowired UserService users;
    @Autowired RecipeService recipeService;
    @Autowired RecipeWorkflowService workflow;
    @Autowired RecipeRepository recipes;
    @Autowired NotificationService notifications;
    @Autowired ReminderService reminders;
    @Autowired AttachmentService attachments;
    @Autowired CommentService comments;
    @Autowired CollectionService collections;
    @Autowired PreferencesService preferences;
    @Autowired ImportService importer;
    @Autowired ReportService reports;
    @Autowired CacheManager cacheManager;

    MockMvc mvc;
    UserDto admin, chemist, vet;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        admin = users.login("admin@crms.local", "Admin123!").orElseThrow();
        chemist = users.login("chemist@crms.local", "Pass123!").orElseThrow();
        vet = users.login("vet@crms.local", "Pass123!").orElseThrow();
    }

    MockHttpSession session(UserDto user) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("user", user);
        s.setAttribute("prefs", preferences.get(user.getId()));
        return s;
    }

    Long newRecipe(String code) {
        RecipeForm f = new RecipeForm();
        f.setCode(code);
        f.setName("Feature " + code);
        f.setPrice(4.0);
        f.setIngredients("Distilled Water, 100, ml");
        return recipeService.save(f).getId();
    }

    // ---- #9 workflow
    @Test
    void workflowOnlyAllowsValidTransitionsAndReviewerRights() {
        Long id = newRecipe("RX-W-1"); // created outside a request = system data = already APPROVED
        var ex = assertThrows(IllegalArgumentException.class, () -> workflow.change(id, RecipeStatus.IN_REVIEW, null, chemist));
        assertTrue(ex.getMessage().contains("cannot be moved"), ex.getMessage());
        workflow.change(id, RecipeStatus.DRAFT, null, chemist);
        workflow.change(id, RecipeStatus.IN_REVIEW, null, chemist);
        assertThrows(ForbiddenException.class, () -> workflow.change(id, RecipeStatus.APPROVED, null, chemist));
        assertThrows(IllegalArgumentException.class, () -> workflow.change(id, RecipeStatus.REJECTED, " ", admin));
        assertEquals("APPROVED", workflow.change(id, RecipeStatus.APPROVED, "ok", admin).getStatus());
        assertEquals(3, workflow.history(id).size());
    }

    // ---- #8 versions
    @Test
    void everySaveIsAVersionAndRestoreAddsANewOne() {
        Long id = newRecipe("RX-V-1");
        RecipeForm f = recipeService.getForm(id);
        f.setName("Renamed");
        recipeService.save(f);
        assertEquals(2, recipeService.history(id).size());
        assertTrue(recipeService.diff(id, 1, 2).stream().anyMatch(d -> d.field().equals("name") && d.changed()));
        assertEquals("Feature RX-V-1", recipeService.restoreVersion(id, 1).getName());
        assertEquals(3, recipeService.history(id).size());
        assertThrows(NotFoundException.class, () -> recipeService.restoreVersion(id, 99));
    }

    // ---- #3 search
    @Test
    void combinedFiltersWorkAndEmptyOnesAreIgnored() {
        RecipeSearch all = new RecipeSearch();
        long total = recipeService.search(all, "name", "asc", 0, 100).getTotalElements();
        assertTrue(total >= 10);
        RecipeSearch s = new RecipeSearch();
        s.setQ("spray");
        s.setMaxPrice(9.0);
        var found = recipeService.search(s, "price", "desc", 0, 5).getContent();
        assertFalse(found.isEmpty());
        assertTrue(found.stream().allMatch(r -> r.getName().toLowerCase().contains("spray") && r.getPrice() <= 9.0));
        s.setQ("no-such-recipe-xyz");
        assertTrue(recipeService.search(s, "name", "asc", 0, 5).isEmpty());
    }

    // ---- #6 export
    @Test
    void csvExportHasBomAndHeaderAndFollowsFilters() throws Exception {
        String csv = mvc.perform(get("/recipes/export").session(session(vet)).param("q", "Antiseptic"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("recipes.csv")))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿\"code\""));
        assertTrue(csv.contains("Antiseptic Spray") && !csv.contains("Wound Wash"));
        mvc.perform(get("/recipes/export").session(session(vet)).param("format", "xlsx"))
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("'=1+1", ExportService.safe("=1+1"));
    }

    // ---- #7 import
    @Test
    void importPreviewFindsProblemsAndAtomicModeSavesNothing() throws Exception {
        String csv = "code,name,price,ingredients,signalWord\n"
            + "RX-I-1,Import One,2.5,Water:100:ml,NONE\n"
            + "RX-1001,Duplicate,2.5,Water:100:ml,NONE\n"
            + "RX-I-3,Bad Price,abc,Water:100:ml,NONE\n";
        var preview = importer.preview(new MockMultipartFile("file", "r.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, preview.count("OK"));
        assertEquals(1, preview.count("DUPLICATE"));
        assertEquals(1, preview.count("ERROR"));
        assertThrows(IllegalArgumentException.class, () -> importer.commit(preview, true));
        assertTrue(recipes.findByCode("RX-I-1").isEmpty(), "atomic import must not save anything");
        assertEquals(1, importer.commit(preview, false));
        assertTrue(recipes.findByCode("RX-I-1").isPresent());
        assertThrows(IllegalArgumentException.class, () -> importer.preview(new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1})));
    }

    // ---- #4 notifications + #14 preferences
    @Test
    void statusChangeNotifiesTheOwnerAndPreferencesCanSilenceIt() throws Exception {
        mvc.perform(post("/recipes/save").session(session(chemist)).param("code", "RX-N-1").param("name", "Notify Me")
            .param("price", "3").param("ingredients", "Water, 1, ml"));
        Long id = recipes.findByCode("RX-N-1").orElseThrow().getId();
        long before = notifications.unread(chemist.getId());
        workflow.change(id, RecipeStatus.IN_REVIEW, null, chemist);
        workflow.change(id, RecipeStatus.APPROVED, "good", admin);
        assertEquals(before + 1, notifications.unread(chemist.getId()));
        var mine = notifications.page(chemist.getId(), 0, 50).getContent();
        assertThrows(NotFoundException.class, () -> notifications.markRead(mine.get(0).id(), admin.getId()));
        notifications.markRead(mine.get(0).id(), chemist.getId());

        preferences.save(chemist.getId(), Theme.DARK, 20, false, true);
        long afterApprove = notifications.unread(chemist.getId());
        workflow.change(id, RecipeStatus.DRAFT, null, admin);
        assertEquals(afterApprove, notifications.unread(chemist.getId()), "status notifications are switched off");
        PreferencesDto saved = preferences.get(chemist.getId());
        assertEquals(Theme.DARK, saved.theme());
        assertEquals(20, saved.pageSize());
        assertThrows(IllegalArgumentException.class, () -> preferences.save(chemist.getId(), Theme.DARK, 7, true, true));
        preferences.save(chemist.getId(), Theme.SYSTEM, 10, true, true);
    }

    // ---- #10 reminders
    @Test
    void reminderJobIsIdempotent() {
        Long id = newRecipe("RX-R-1");
        workflow.change(id, RecipeStatus.DRAFT, null, chemist);
        workflow.change(id, RecipeStatus.IN_REVIEW, null, chemist);
        int first = reminders.run(LocalDateTime.now().plusDays(10));
        assertTrue(first >= 1);
        assertEquals(0, reminders.run(LocalDateTime.now().plusDays(10)), "second run must not create duplicates");
        assertEquals(0, reminders.run(LocalDateTime.now()), "nothing is overdue today");
    }

    // ---- #5 attachments
    @Test
    void onlyRealSmallFilesAreAcceptedAndOthersCannotOpenThem() throws Exception {
        Long id = newRecipe("RX-A-1");
        attachments.upload(id, new MockMultipartFile("file", "photo.png", "image/png", PNG), chemist);
        assertThrows(IllegalArgumentException.class, () -> attachments.upload(id, new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()), chemist));
        assertThrows(IllegalArgumentException.class, () -> attachments.upload(id, new MockMultipartFile("file", "run.exe", "application/x", PNG), chemist));
        assertThrows(IllegalArgumentException.class, () -> attachments.upload(id, new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]), chemist));
        Long fileId = attachments.list(id, chemist).get(0).id();
        assertEquals("photo.png", attachments.download(fileId, chemist).name());
        assertEquals("photo.png", attachments.download(fileId, admin).name());
        assertThrows(ForbiddenException.class, () -> attachments.download(fileId, vet));
        assertThrows(ForbiddenException.class, () -> attachments.delete(fileId, vet));
    }

    // ---- #12 comments
    @Test
    void commentsThreadEditAndModerationRules() {
        Long id = newRecipe("RX-C-1");
        comments.add(id, null, "First!", vet);
        var root = comments.page(id, 0, 5, vet).getContent().get(0);
        comments.add(id, root.id(), "A reply", chemist);
        assertEquals(1, comments.page(id, 0, 5, vet).getContent().get(0).replies().size());
        assertThrows(ForbiddenException.class, () -> comments.edit(root.id(), "hijack", chemist));
        comments.edit(root.id(), "Edited", vet);
        assertThrows(IllegalArgumentException.class, () -> comments.add(id, null, "   ", vet));
        comments.delete(root.id(), admin);
        assertTrue(comments.page(id, 0, 5, vet).getContent().get(0).deleted());
    }

    // ---- #13 collections
    @Test
    void sameRecipeCannotBeAddedTwiceToOneList() {
        Long id = newRecipe("RX-L-1");
        collections.create(vet.getId(), "Winter " + id);
        Long listId = collections.list(vet.getId()).stream().filter(l -> l.name().equals("Winter " + id)).findFirst().orElseThrow().id();
        assertTrue(collections.add(vet.getId(), listId, id));
        assertFalse(collections.add(vet.getId(), listId, id));
        assertThrows(IllegalArgumentException.class, () -> collections.create(vet.getId(), "Winter " + id));
        assertThrows(NotFoundException.class, () -> collections.add(chemist.getId(), listId, id), "other users' lists are invisible");
        assertTrue(collections.toggleFavorite(vet.getId(), id));
        assertFalse(collections.toggleFavorite(vet.getId(), id));
    }

    // ---- #19 cache
    @Test
    void reportCacheIsFilledAndEvictedWhenRecipesChange() {
        ConcurrentMapCache cache = (ConcurrentMapCache) cacheManager.getCache("reports");
        cache.clear();
        reports.monthly(1, LocalDate.now());
        assertFalse(cache.getNativeCache().isEmpty());
        newRecipe("RX-K-1");
        assertTrue(cache.getNativeCache().isEmpty());
    }

    // ---- #16 REST API
    @Test
    void apiUsesCorrectStatusCodes() throws Exception {
        String body = "{\"code\":\"RX-API-1\",\"name\":\"Api Made\",\"price\":2.5,\"ingredients\":[{\"name\":\"Water\",\"quantity\":10,\"unit\":\"ml\"}]}";
        mvc.perform(get("/api/v1/recipes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/recipes").session(session(vet)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/recipes").session(session(chemist)).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"\",\"price\":-1,\"ingredients\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Validation failed")));
        mvc.perform(post("/api/v1/recipes").session(session(chemist)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"));
        Long id = recipes.findByCode("RX-API-1").orElseThrow().getId();
        mvc.perform(get("/api/v1/recipes/" + id).session(session(vet))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recipes/999999").session(session(vet))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/recipes/" + id).session(session(chemist))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/recipes/" + id).session(session(vet))).andExpect(status().isNotFound());
    }

    // ---- pages + upload over HTTP
    @Test
    void newPagesRenderAndUploadWorksOverHttp() throws Exception {
        Long id = recipes.findByCode("RX-1005").orElseThrow().getId();
        for (String page : List.of("/", "/?months=12", "/recipes/" + id, "/recipes/" + id + "/history", "/recipes/import", "/collections",
            "/notifications", "/settings", "/admin/audit", "/recipes?status=APPROVED&signalWord=DANGER&minPrice=1&sort=price&dir=desc")) {
            mvc.perform(get(page).session(session(admin))).andExpect(status().isOk());
        }
        mvc.perform(multipart("/recipes/" + id + "/attachments").file(new MockMultipartFile("file", "a.png", "image/png", PNG)).session(session(chemist)))
            .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/recipes/" + id + "/attachments").file(new MockMultipartFile("file", "a.png", "image/png", PNG)).session(session(vet)))
            .andExpect(status().isForbidden());
    }
}
