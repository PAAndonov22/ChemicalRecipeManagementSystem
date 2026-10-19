package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.dto.CategoryDto;
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

    public RecipeService(RecipeRepository recipes, IngredientRepository ingredients, CategoryRepository categories) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.categories = categories;
    }

    public List<RecipeDto> search(String name) {
        return recipes.findByNameContainingIgnoreCaseOrderByName(name.trim()).stream()
            .map(DtoMapper::toRecipeDto)
            .toList();
    }

    public RecipeDto get(Long id) {
        return DtoMapper.toRecipeDto(find(id));
    }

    /** The recipe as a filled form, for the edit page. */
    public RecipeForm getForm(Long id) {
        return DtoMapper.toRecipeForm(find(id));
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

        Recipe recipe = form.getId() == null ? new Recipe() : find(form.getId());
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

        return DtoMapper.toRecipeDto(recipes.save(recipe));
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
        return recipes.findById(id).orElseThrow(() -> new IllegalArgumentException("Recipe not found."));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
