package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Recipes: the list uses RecipeDto, the create / edit form uses RecipeForm - never the Recipe entity. */
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
        return showForm(new RecipeForm(), model);
    }

    @GetMapping("/recipes/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return showForm(recipeService.getForm(id), model);
    }

    @PostMapping("/recipes/save")
    public String save(@ModelAttribute("recipe") RecipeForm form, RedirectAttributes redirect) {
        RecipeDto saved = recipeService.save(form);
        redirect.addFlashAttribute("success", "Recipe " + saved.getName() + " saved.");
        return "redirect:/recipes";
    }

    private String showForm(RecipeForm form, Model model) {
        model.addAttribute("recipe", form);
        model.addAttribute("categories", recipeService.allCategories());
        model.addAttribute("signalWords", SignalWord.values());
        return "recipes/form";
    }
}
