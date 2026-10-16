package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RecipeController {
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/recipes")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("recipes", recipeService.search(q));
        model.addAttribute("q", q);
        return "recipes/list";
    }

    @GetMapping("/recipes/new")
    public String newForm(Model model) {
        model.addAttribute("recipe", new Recipe());
        return "recipes/form";
    }

    @GetMapping("/recipes/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("recipe", recipeService.get(id));
        return "recipes/form";
    }

    @PostMapping("/recipes/save")
    public String save(@ModelAttribute Recipe recipe, RedirectAttributes redirect) {
        recipeService.save(recipe);
        redirect.addFlashAttribute("success", "Recipe " + recipe.getName() + " saved.");
        return "redirect:/recipes";
    }
}
