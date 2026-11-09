package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.RequirePermission;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpSession;
import org.codingburgas.ChemicalRecipeSystem.dto.PreferencesDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeSearch;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Recipes: lists use RecipeDto, the form uses RecipeForm - never the Recipe entity. Rights: see @RequirePermission. */
@Controller
public class RecipeController {
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/recipes")
    @RequirePermission(Permission.RECIPE_READ)
    public String list(@ModelAttribute("search") RecipeSearch search, @RequestParam(defaultValue = "name") String sort,
                       @RequestParam(defaultValue = "asc") String dir, @RequestParam(defaultValue = "0") int page,
                       HttpSession session, Model model) {
        PreferencesDto prefs = (PreferencesDto) session.getAttribute("prefs");
        Page<RecipeDto> result = recipeService.search(search, sort, dir, page, prefs == null ? 10 : prefs.pageSize());
        model.addAttribute("recipes", result.getContent());
        model.addAttribute("recipePage", result);
        model.addAttribute("pager", result);
        model.addAttribute("categories", recipeService.allCategories());
        model.addAttribute("signalWords", SignalWord.values());
        model.addAttribute("statuses", RecipeStatus.values());
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "recipes/list";
    }

    @GetMapping("/recipes/new")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String newForm(Model model) {
        return showForm(new RecipeForm(), model);
    }

    @GetMapping("/recipes/{id}/edit")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String editForm(@PathVariable Long id, Model model) {
        return showForm(recipeService.getForm(id), model);
    }

    @PostMapping("/recipes/save")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String save(@ModelAttribute("recipe") RecipeForm form, RedirectAttributes redirect) {
        RecipeDto saved = recipeService.save(form);
        redirect.addFlashAttribute("success", "Recipe " + saved.getName() + " saved.");
        return "redirect:/recipes";
    }

    @PostMapping("/recipes/{id}/delete")
    @RequirePermission(Permission.RECIPE_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        recipeService.delete(id);
        redirect.addFlashAttribute("success", "Recipe moved to the trash.");
        return "redirect:/recipes";
    }

    @GetMapping("/recipes/trash")
    @RequirePermission(Permission.TRASH_MANAGE)
    public String trash(Model model) {
        model.addAttribute("recipes", recipeService.trash());
        return "recipes/trash";
    }

    @PostMapping("/recipes/{id}/restore")
    @RequirePermission(Permission.TRASH_MANAGE)
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        recipeService.restore(id);
        redirect.addFlashAttribute("success", "Recipe restored.");
        return "redirect:/recipes/trash";
    }

    @PostMapping("/recipes/{id}/purge")
    @RequirePermission(Permission.TRASH_MANAGE)
    public String purge(@PathVariable Long id, RedirectAttributes redirect) {
        recipeService.purge(id);
        redirect.addFlashAttribute("success", "Recipe deleted permanently.");
        return "redirect:/recipes/trash";
    }

    private String showForm(RecipeForm form, Model model) {
        model.addAttribute("recipe", form);
        model.addAttribute("categories", recipeService.allCategories());
        model.addAttribute("signalWords", SignalWord.values());
        return "recipes/form";
    }
}
