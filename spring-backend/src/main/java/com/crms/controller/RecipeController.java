package com.crms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.service.AuthService;
import com.crms.service.RecipeService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private final AuthService authService;
    private final RecipeService recipeService;

    public RecipeController(AuthService authService, RecipeService recipeService) {
        this.authService = authService;
        this.recipeService = recipeService;
    }

    @GetMapping
    public Map<String, Object> listRecipes(HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.listRecipes(actor, request);
    }

    @GetMapping("/{recipeId}")
    public Map<String, Object> getRecipe(@PathVariable int recipeId, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.getRecipe(recipeId, actor);
    }

    @GetMapping("/{recipeId}/versions")
    public Map<String, Object> versions(@PathVariable int recipeId, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.getVersions(recipeId, actor);
    }

    @GetMapping("/{recipeId}/compare")
    public Map<String, Object> compare(@PathVariable int recipeId, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.compareVersions(recipeId, actor, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.createRecipe(actor, payload, request.getRemoteAddr());
    }

    @PutMapping("/{recipeId}")
    public Map<String, Object> update(@PathVariable int recipeId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.updateRecipe(recipeId, actor, payload, request.getRemoteAddr());
    }

    @PostMapping("/{recipeId}/share")
    public Map<String, Object> share(@PathVariable int recipeId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.shareRecipe(recipeId, actor, payload, request.getRemoteAddr());
    }

    @PostMapping("/{recipeId}/submit")
    public Map<String, Object> submit(@PathVariable int recipeId, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.submitForApproval(recipeId, actor, request.getRemoteAddr());
    }

    @PostMapping("/{recipeId}/review")
    public Map<String, Object> review(@PathVariable int recipeId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        AuthenticatedUser actor = authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return recipeService.reviewRecipe(recipeId, actor, payload, request.getRemoteAddr());
    }
}
