package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
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

    public RecipeService(RecipeRepository recipes, IngredientRepository ingredients, CategoryRepository categories,
                         AuditService audit, OrderRepository orders) {
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
    public RecipeDto save(RecipeForm form) {
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
        } else if (!after.equals(before)) {
            audit.log("UPDATE", "Recipe", recipe.getId(), "before: " + before + " | after: " + after);
        }
        return DtoMapper.toRecipeDto(recipe);
    }

    private static String describe(Recipe r) {
        return r.getCode() + " / " + r.getName() + " / " + r.getPrice() + " EUR / "
            + r.getIngredients().stream().map(l -> DtoMapper.toRecipeIngredientDto(l).getLabel()).collect(Collectors.joining(", "));
    }

    /** Soft delete: the recipe goes to the trash, the row stays in the database. */
    @Transactional
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
