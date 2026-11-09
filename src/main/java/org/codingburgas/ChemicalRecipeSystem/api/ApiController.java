package org.codingburgas.ChemicalRecipeSystem.api;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.RequirePermission;
import org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper;
import org.codingburgas.ChemicalRecipeSystem.dto.OrderDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeSearch;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** JSON API next to the web pages. Uses DTOs only; same login session and same permissions as the pages. */
@RestController
@RequestMapping("/api/v1")
public class ApiController {
    public record PageResponse<T>(List<T> items, int page, int size, long total) {
    }

    private final RecipeService recipes;
    private final OrderService orders;

    public ApiController(RecipeService recipes, OrderService orders) {
        this.recipes = recipes;
        this.orders = orders;
    }

    @GetMapping("/recipes")
    @RequirePermission(Permission.RECIPE_READ)
    public PageResponse<RecipeDto> list(@ModelAttribute RecipeSearch search, @RequestParam(defaultValue = "name") String sort,
                                        @RequestParam(defaultValue = "asc") String dir, @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        var result = recipes.search(search, sort, dir, page, Math.min(Math.max(size, 1), 100));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/recipes/{id}")
    @RequirePermission(Permission.RECIPE_READ)
    public RecipeDto get(@PathVariable Long id) {
        return recipes.get(id);
    }

    @PostMapping("/recipes")
    @RequirePermission(Permission.RECIPE_WRITE)
    public ResponseEntity<RecipeDto> create(@Valid @RequestBody RecipeRequest request) {
        RecipeDto saved = recipes.save(toForm(request, null));
        return ResponseEntity.created(URI.create("/api/v1/recipes/" + saved.getId())).body(saved);
    }

    @PutMapping("/recipes/{id}")
    @RequirePermission(Permission.RECIPE_WRITE)
    public RecipeDto update(@PathVariable Long id, @Valid @RequestBody RecipeRequest request) {
        return recipes.save(toForm(request, id));
    }

    @DeleteMapping("/recipes/{id}")
    @RequirePermission(Permission.RECIPE_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipes.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders")
    @RequirePermission(Permission.RECIPE_READ)
    public List<OrderDto> orders(@RequestParam(defaultValue = "date") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return orders.search(null, null, null, null, sort, dir.equals("desc"));
    }

    private static RecipeForm toForm(RecipeRequest r, Long id) {
        RecipeForm f = new RecipeForm();
        f.setId(id);
        f.setVersion(r.version());
        f.setCode(r.code());
        f.setName(r.name());
        f.setDescription(r.description());
        f.setPrice(r.price());
        f.setIngredients(r.ingredients().stream()
            .map(l -> l.name() + ", " + DtoMapper.formatQuantity(l.quantity()) + ", " + (l.unit() == null || l.unit().isBlank() ? "ml" : l.unit()))
            .collect(Collectors.joining("\n")));
        f.setCategoryIds(r.categoryIds() == null ? List.of() : r.categoryIds());
        f.setHazardClass(r.hazardClass());
        f.setSignalWord(r.signalWord() == null || r.signalWord().isBlank() ? SignalWord.NONE : SignalWord.valueOf(r.signalWord().toUpperCase()));
        f.setStorageConditions(r.storageConditions());
        f.setProtectiveEquipment(r.protectiveEquipment());
        return f;
    }
}
