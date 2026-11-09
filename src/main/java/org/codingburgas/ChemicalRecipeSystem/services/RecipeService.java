package org.codingburgas.ChemicalRecipeSystem.services;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Properties;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeSearch;
import org.codingburgas.ChemicalRecipeSystem.models.Category;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeVersion;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.repositories.AttachmentRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.CollectionRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.CommentRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeDecisionRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeVersionRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.config.CurrentUser;
import org.codingburgas.ChemicalRecipeSystem.dto.CategoryDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ConflictException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.models.Ingredient;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.SafetyDataSheet;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.repositories.CategoryRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.IngredientRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecipeService {
    /** One parsed line of the ingredients text: "name, quantity, unit". */
    record IngredientLine(String name, double quantity, String unit) {}

    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final OrderRepository orders;
    private final UserRepository users;
    private final RecipeVersionRepository versionRepo;
    private final RecipeDecisionRepository decisionRepo;
    private final AttachmentService attachments;
    private final CommentRepository commentRepo;
    private final CollectionRepository collectionRepo;
    private final NotificationService notifications;

    public record VersionDto(int no, LocalDateTime at, String by, String note) {
    }

    public record DiffRow(String field, String before, String after) {
        public boolean changed() { return !before.equals(after); }
    }

    private static final java.util.List<String> SNAPSHOT_FIELDS = java.util.List.of("code", "name", "price", "description",
        "categories", "ingredients", "hazardClass", "signalWord", "storageConditions", "protectiveEquipment");
    private static final Set<String> SORTABLE = Set.of("name", "code", "price", "createdAt", "status");

    public RecipeService(RecipeRepository recipes, IngredientRepository ingredients, CategoryRepository categories,
                         AuditService audit, OrderRepository orders, UserRepository users, RecipeVersionRepository versionRepo,
                         RecipeDecisionRepository decisionRepo, AttachmentService attachments, CommentRepository commentRepo,
                         CollectionRepository collectionRepo, NotificationService notifications) {
        this.users = users;
        this.versionRepo = versionRepo;
        this.decisionRepo = decisionRepo;
        this.attachments = attachments;
        this.commentRepo = commentRepo;
        this.collectionRepo = collectionRepo;
        this.notifications = notifications;
        this.audit = audit;
        this.orders = orders;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.categories = categories;
    }

    public List<RecipeDto> search(String name) {
        return recipes.findByNameContainingIgnoreCaseAndDeletedAtIsNullOrderByName(name.trim()).stream()
            .map(DtoMapper::toRecipeDto)
            .toList();
    }

    /** Combined filters + sorting + paging. Empty criteria are ignored. */
    public Page<RecipeDto> search(RecipeSearch s, String sort, String dir, int page, int size) {
        Sort.Direction d = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String field = SORTABLE.contains(sort) ? sort : "name";
        return recipes.findAll(spec(s), PageRequest.of(Math.max(page, 0), size, Sort.by(d, field).and(Sort.by("id"))))
            .map(DtoMapper::toRecipeDto);
    }

    /** All matches without paging - used by the export, so it follows the same filters. */
    public List<RecipeDto> searchAll(RecipeSearch s) {
        return recipes.findAll(spec(s), Sort.by("name")).stream().map(DtoMapper::toRecipeDto).toList();
    }

    static Specification<Recipe> spec(RecipeSearch s) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isNull(root.get("deletedAt")));
            if (!isBlank(s.getQ())) {
                String like = "%" + s.getQ().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like)));
            }
            if (s.getCategoryId() != null) {
                p.add(cb.equal(root.join("categories").get("id"), s.getCategoryId()));
            }
            if (s.getSignalWord() != null) {
                p.add(cb.equal(root.join("safetyDataSheet").get("signalWord"), s.getSignalWord()));
            }
            if (s.getStatus() != null) {
                p.add(cb.equal(root.get("status"), s.getStatus()));
            }
            if (s.getMinPrice() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Double>get("price"), s.getMinPrice()));
            }
            if (s.getMaxPrice() != null) {
                p.add(cb.lessThanOrEqualTo(root.<Double>get("price"), s.getMaxPrice()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Validates a form without saving it (used by the import preview). */
    public void check(RecipeForm form) {
        if (isBlank(form.getCode()) || isBlank(form.getName())) {
            throw new IllegalArgumentException("Code and name are required.");
        }
        if (form.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0.");
        }
        parseIngredients(form.getIngredients());
    }

    // ---------- versions ----------
    private String snapshot(Recipe r) {
        RecipeForm f = DtoMapper.toRecipeForm(r);
        Properties p = new Properties();
        p.setProperty("code", nz(f.getCode()));
        p.setProperty("name", nz(f.getName()));
        p.setProperty("price", String.valueOf(f.getPrice()));
        p.setProperty("description", nz(f.getDescription()));
        p.setProperty("categories", r.getCategories().stream().map(Category::getName).sorted().collect(Collectors.joining(",")));
        p.setProperty("ingredients", nz(f.getIngredients()));
        p.setProperty("hazardClass", nz(f.getHazardClass()));
        p.setProperty("signalWord", f.getSignalWord() == null ? "NONE" : f.getSignalWord().name());
        p.setProperty("storageConditions", nz(f.getStorageConditions()));
        p.setProperty("protectiveEquipment", nz(f.getProtectiveEquipment()));
        StringWriter out = new StringWriter();
        try {
            p.store(out, null);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out.toString();
    }

    private Properties loadVersion(Long recipeId, int no) {
        RecipeVersion v = versionRepo.findByRecipeIdAndVersionNo(recipeId, no).orElseThrow(() -> new NotFoundException("Version not found."));
        Properties p = new Properties();
        try {
            p.load(new StringReader(v.getSnapshot()));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return p;
    }

    private void recordVersion(Recipe r, String note) {
        versionRepo.save(new RecipeVersion(r.getId(), versionRepo.countByRecipeId(r.getId()) + 1, snapshot(r), CurrentUser.name(), note));
    }

    public List<VersionDto> history(Long id) {
        findActive(id);
        return versionRepo.findByRecipeIdOrderByVersionNoDesc(id).stream()
            .map(v -> new VersionDto(v.getVersionNo(), v.getCreatedAt(), v.getChangedBy(), v.getNote())).toList();
    }

    /** Field by field comparison of two saved versions. */
    public List<DiffRow> diff(Long id, int a, int b) {
        findActive(id);
        Properties pa = loadVersion(id, a);
        Properties pb = loadVersion(id, b);
        return SNAPSHOT_FIELDS.stream().map(k -> new DiffRow(k, pa.getProperty(k, ""), pb.getProperty(k, ""))).toList();
    }

    /** Restoring never deletes history: the old data is saved as a NEW version. */
    @Transactional
    @CacheEvict(cacheNames = {"reports", "stats"}, allEntries = true)
    public RecipeDto restoreVersion(Long id, int versionNo) {
        findActive(id);
        Properties p = loadVersion(id, versionNo);
        RecipeForm f = new RecipeForm();
        f.setId(id);
        f.setCode(p.getProperty("code"));
        f.setName(p.getProperty("name"));
        f.setPrice(Double.parseDouble(p.getProperty("price", "0")));
        f.setDescription(p.getProperty("description"));
        f.setIngredients(p.getProperty("ingredients"));
        List<String> names = Arrays.asList(p.getProperty("categories", "").split(","));
        f.setCategoryIds(categories.findAll().stream().filter(c -> names.contains(c.getName())).map(Category::getId).toList());
        f.setHazardClass(p.getProperty("hazardClass"));
        f.setSignalWord(SignalWord.valueOf(p.getProperty("signalWord", "NONE")));
        f.setStorageConditions(p.getProperty("storageConditions"));
        f.setProtectiveEquipment(p.getProperty("protectiveEquipment"));
        RecipeDto result = saveInternal(f, "Restored from version " + versionNo);
        audit.log("RESTORE_VERSION", "Recipe", id, "Restored version " + versionNo);
        return result;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public RecipeDto get(Long id) {
        return DtoMapper.toRecipeDto(findActive(id));
    }

    /** The recipe as a filled form, for the edit page. */
    public RecipeForm getForm(Long id) {
        return DtoMapper.toRecipeForm(findActive(id));
    }

    public List<CategoryDto> allCategories() {
        return categories.findAllByOrderByNameAsc().stream().map(DtoMapper::toCategoryDto).toList();
    }

    /** Creates a new recipe (form id == null) or updates an existing one, with its lines, categories and safety sheet. */
    @Transactional
    @CacheEvict(cacheNames = {"reports", "stats"}, allEntries = true)
    public RecipeDto save(RecipeForm form) {
        return saveInternal(form, null);
    }

    private RecipeDto saveInternal(RecipeForm form, String note) {
        if (isBlank(form.getCode()) || isBlank(form.getName())) {
            throw new IllegalArgumentException("Code and name are required.");
        }
        if (form.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0.");
        }
        List<IngredientLine> lines = parseIngredients(form.getIngredients());

        boolean isNew = form.getId() == null;
        Recipe recipe = isNew ? new Recipe() : findActive(form.getId());
        // optimistic locking: the form carries the version it was loaded with
        if (!isNew && form.getVersion() != null && !form.getVersion().equals(recipe.getVersion())) {
            throw new ConflictException("This recipe was changed by someone else while you were editing. Your changes were not saved - the page now shows the latest data.");
        }
        String before = isNew ? null : describe(recipe);
        String code = form.getCode().trim();
        recipes.findByCode(code)
            .filter(other -> !other.getId().equals(recipe.getId()))
            .ifPresent(other -> {
                throw new IllegalArgumentException("Code " + code + " is already used by " + other.getName() + ".");
            });

        recipe.setCode(code);
        recipe.setName(form.getName().trim());
        recipe.setDescription(form.getDescription());
        recipe.setPrice(form.getPrice());
        if (isNew && CurrentUser.id() != null) {
            users.findById(CurrentUser.id()).ifPresent(recipe::setOwner);
        }

        // composition: old lines are removed (orphanRemoval deletes them), new ones are added
        recipe.clearIngredients();
        for (IngredientLine line : lines) {
            recipe.addIngredient(findOrCreateIngredient(line.name()), line.quantity(), line.unit());
        }

        // many-to-many
        List<Long> categoryIds = form.getCategoryIds() == null ? List.of() : form.getCategoryIds();
        recipe.setCategories(categories.findAllById(categoryIds));

        // one-to-one: every recipe gets exactly one safety data sheet
        SafetyDataSheet sheet = recipe.getSafetyDataSheet();
        if (sheet == null) {
            sheet = new SafetyDataSheet();
            recipe.setSafetyDataSheet(sheet);
        }
        sheet.setHazardClass(isBlank(form.getHazardClass()) ? "Not classified" : form.getHazardClass().trim());
        sheet.setSignalWord(form.getSignalWord() == null ? SignalWord.NONE : form.getSignalWord());
        sheet.setStorageConditions(form.getStorageConditions());
        sheet.setProtectiveEquipment(form.getProtectiveEquipment());
        sheet.setRevisionDate(LocalDate.now());

        if (isNew && CurrentUser.get() == null) {
            recipe.setStatus(RecipeStatus.APPROVED); // seed / system data needs no review
        }
        recipes.saveAndFlush(recipe);
        String after = describe(recipe);
        if (isNew) {
            audit.log("CREATE", "Recipe", recipe.getId(), after);
            recordVersion(recipe, note == null ? "Created" : note);
            if (CurrentUser.get() != null) { // seed data does not notify anyone
                notifications.notifyRole(Role.ADMIN, "STATUS", "New recipe " + recipe.getName() + " was created by " + CurrentUser.name() + ".",
                    "/recipes/" + recipe.getId(), CurrentUser.id());
            }
        } else if (!after.equals(before)) {
            audit.log("UPDATE", "Recipe", recipe.getId(), "before: " + before + " | after: " + after);
            recordVersion(recipe, note == null ? "Edited" : note);
        }
        return DtoMapper.toRecipeDto(recipe);
    }

    private static String describe(Recipe r) {
        return r.getCode() + " / " + r.getName() + " / " + r.getPrice() + " EUR / "
            + r.getIngredients().stream().map(l -> DtoMapper.toRecipeIngredientDto(l).getLabel()).collect(Collectors.joining(", "));
    }

    /** Soft delete: the recipe goes to the trash, the row stays in the database. */
    @Transactional
    @CacheEvict(cacheNames = {"reports", "stats"}, allEntries = true)
    public void delete(Long id) {
        Recipe recipe = findActive(id);
        recipe.markDeleted(CurrentUser.name());
        audit.log("DELETE", "Recipe", id, recipe.getCode() + " " + recipe.getName() + " moved to trash");
    }

    public List<RecipeDto> trash() {
        return recipes.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream().map(DtoMapper::toRecipeDto).toList();
    }

    @Transactional
    public void restore(Long id) {
        Recipe recipe = find(id);
        if (!recipe.isDeleted()) {
            throw new IllegalArgumentException("This recipe is not in the trash.");
        }
        recipe.restore();
        audit.log("RESTORE", "Recipe", id, recipe.getCode() + " restored from trash");
    }

    /** Permanent delete - only for recipes already in the trash and without orders. */
    @Transactional
    public void purge(Long id) {
        Recipe recipe = find(id);
        if (!recipe.isDeleted()) {
            throw new IllegalArgumentException("Only recipes in the trash can be deleted permanently.");
        }
        if (orders.existsByRecipeId(id)) {
            throw new IllegalArgumentException("This recipe has orders, so it can stay in the trash but not be deleted permanently.");
        }
        versionRepo.deleteByRecipeId(id);
        decisionRepo.deleteByRecipeId(id);
        attachments.deleteAllFor(id);
        commentRepo.detachReplies(id);
        commentRepo.deleteAllFor(id);
        collectionRepo.findContaining(id).forEach(c -> c.getRecipes().remove(recipe));
        recipes.delete(recipe);
        audit.log("PURGE", "Recipe", id, recipe.getCode() + " " + recipe.getName() + " deleted permanently");
    }

    private Recipe findActive(Long id) {
        return recipes.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("Recipe not found."));
    }

    /**
     * Parses the ingredients text, one ingredient per line: "name, quantity, unit".
     * Quantity and unit are optional (default 0 ml).
     */
    static List<IngredientLine> parseIngredients(String text) {
        List<IngredientLine> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : (text == null ? "" : text).split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            String name = parts[0].trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Missing ingredient name in line: " + line);
            }
            if (!seen.add(name.toLowerCase())) {
                throw new IllegalArgumentException(name + " is listed twice.");
            }
            double quantity = 0;
            if (parts.length > 1 && !parts[1].isBlank()) {
                try {
                    quantity = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Wrong quantity in line: " + line);
                }
            }
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative: " + line);
            }
            String unit = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : "ml";
            result.add(new IngredientLine(name, quantity, unit));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("A recipe needs at least one ingredient.");
        }
        return result;
    }

    private Ingredient findOrCreateIngredient(String name) {
        return ingredients.findByNameIgnoreCase(name)
            .orElseGet(() -> ingredients.save(new Ingredient(name, null)));
    }

    private Recipe find(Long id) {
        return recipes.findById(id).orElseThrow(() -> new NotFoundException("Recipe not found."));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
