package org.codingburgas.ChemicalRecipeSystem.dto;

import java.util.List;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.models.BaseEntity;
import org.codingburgas.ChemicalRecipeSystem.models.Category;
import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeIngredient;
import org.codingburgas.ChemicalRecipeSystem.models.SafetyDataSheet;
import org.codingburgas.ChemicalRecipeSystem.models.User;

/**
 * Converts entities (internal model) into DTOs (what the views get).
 * Services call it inside a transaction, so lazy collections can still be read here.
 */
public final class DtoMapper {
    private DtoMapper() {
    }

    public static UserDto toUserDto(User user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
            user.getRole(), user.isActive(), user.getCreatedAt());
    }

    public static RecipeDto toRecipeDto(Recipe recipe) {
        List<RecipeIngredientDto> lines = recipe.getIngredients().stream()
            .map(DtoMapper::toRecipeIngredientDto)
            .toList();
        List<String> categories = recipe.getCategories().stream()
            .map(Category::getName)
            .sorted()
            .toList();
        return new RecipeDto(recipe.getId(), recipe.getCode(), recipe.getName(), recipe.getDescription(),
            recipe.getPrice(), lines, categories, toSafetyDataSheetDto(recipe.getSafetyDataSheet()));
    }

    public static RecipeIngredientDto toRecipeIngredientDto(RecipeIngredient line) {
        return new RecipeIngredientDto(line.getIngredient().getName(), line.getIngredient().getCasNumber(),
            line.getQuantity(), line.getUnit());
    }

    public static SafetyDataSheetDto toSafetyDataSheetDto(SafetyDataSheet sheet) {
        if (sheet == null) {
            return null;
        }
        return new SafetyDataSheetDto(sheet.getHazardClass(), sheet.getSignalWord(), sheet.getStorageConditions(),
            sheet.getProtectiveEquipment(), sheet.getRevisionDate());
    }

    /** Recipe entity -> the form used to edit it. */
    public static RecipeForm toRecipeForm(Recipe recipe) {
        RecipeForm form = new RecipeForm();
        form.setId(recipe.getId());
        form.setCode(recipe.getCode());
        form.setName(recipe.getName());
        form.setDescription(recipe.getDescription());
        form.setPrice(recipe.getPrice());
        form.setIngredients(recipe.getIngredients().stream()
            .map(l -> l.getIngredient().getName() + ", " + formatQuantity(l.getQuantity()) + ", " + l.getUnit())
            .collect(Collectors.joining("\n")));
        form.setCategoryIds(recipe.getCategories().stream().map(BaseEntity::getId).toList());
        SafetyDataSheet sheet = recipe.getSafetyDataSheet();
        if (sheet != null) {
            form.setHazardClass(sheet.getHazardClass());
            form.setSignalWord(sheet.getSignalWord());
            form.setStorageConditions(sheet.getStorageConditions());
            form.setProtectiveEquipment(sheet.getProtectiveEquipment());
        }
        return form;
    }

    public static OrderDto toOrderDto(Order order) {
        return new OrderDto(order.getId(), order.getOrderedAt(), order.getWeek(),
            order.getRecipe() == null ? null : order.getRecipe().getCode(),
            order.getRecipe() == null ? null : order.getRecipe().getName(),
            order.getCustomer() == null ? null : order.getCustomer().getCompanyName(),
            order.getCreatedBy() == null ? null : order.getCreatedBy().getFullName(),
            order.getQuantity(), order.getTotal());
    }

    public static EventDto toEventDto(Event event) {
        return new EventDto(event.getId(), event.getTitle(), event.getType(), event.getDate(),
            event.getTicketsSold(), event.getTicketPrice(),
            event.getOrganizer() == null ? null : event.getOrganizer().getFullName());
    }

    public static CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getDescription());
    }

    /** 300.0 -> "300", 2.5 -> "2.5". */
    public static String formatQuantity(double quantity) {
        return quantity == Math.rint(quantity) ? String.valueOf((long) quantity) : String.valueOf(quantity);
    }
}
