package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.io.IOException;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.RequirePermission;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.services.AttachmentService;
import org.codingburgas.ChemicalRecipeSystem.services.CollectionService;
import org.codingburgas.ChemicalRecipeSystem.services.CommentService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeWorkflowService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The recipe page: status workflow, files, comments, version history. Rights are re-checked in the services. */
@Controller
public class RecipeDetailController {
    private final RecipeService recipes;
    private final RecipeWorkflowService workflow;
    private final AttachmentService attachments;
    private final CommentService comments;
    private final CollectionService collections;

    public RecipeDetailController(RecipeService recipes, RecipeWorkflowService workflow, AttachmentService attachments,
                                  CommentService comments, CollectionService collections) {
        this.recipes = recipes;
        this.workflow = workflow;
        this.attachments = attachments;
        this.comments = comments;
        this.collections = collections;
    }

    @GetMapping("/recipes/{id}")
    @RequirePermission(Permission.RECIPE_READ)
    public String detail(@PathVariable Long id, @RequestParam(defaultValue = "0") int cpage,
                         @SessionAttribute("user") UserDto me, Model model) {
        var recipe = recipes.get(id);
        model.addAttribute("recipe", recipe);
        model.addAttribute("nextStatuses", RecipeWorkflowService.allowed(RecipeStatus.valueOf(recipe.getStatus()), me.getRole()));
        model.addAttribute("decisions", workflow.history(id));
        model.addAttribute("files", attachments.list(id, me));
        model.addAttribute("commentPage", comments.page(id, cpage, 5, me));
        model.addAttribute("lists", collections.list(me.getId()));
        model.addAttribute("inLists", collections.containing(me.getId(), id));
        return "recipes/detail";
    }

    @PostMapping("/recipes/{id}/status")
    @RequirePermission(Permission.RECIPE_READ)
    public String status(@PathVariable Long id, @RequestParam RecipeStatus to, @RequestParam(required = false) String note,
                         @SessionAttribute("user") UserDto me, RedirectAttributes redirect) {
        workflow.change(id, to, note, me);
        redirect.addFlashAttribute("success", "Status changed to " + to.getLabel() + ".");
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/recipes/{id}/attachments")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String upload(@PathVariable Long id, @RequestParam MultipartFile file, @SessionAttribute("user") UserDto me,
                         RedirectAttributes redirect) throws IOException {
        attachments.upload(id, file, me);
        redirect.addFlashAttribute("success", "File uploaded.");
        return "redirect:/recipes/" + id;
    }

    @GetMapping("/attachments/{id}")
    @RequirePermission(Permission.RECIPE_READ)
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id, @SessionAttribute("user") UserDto me) {
        var file = attachments.download(id, me);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(new FileSystemResource(file.path()));
    }

    @PostMapping("/attachments/{id}/delete")
    @RequirePermission(Permission.RECIPE_READ)
    public String deleteFile(@PathVariable Long id, @RequestParam Long recipeId, @SessionAttribute("user") UserDto me,
                             RedirectAttributes redirect) throws IOException {
        attachments.delete(id, me);
        redirect.addFlashAttribute("success", "File deleted.");
        return "redirect:/recipes/" + recipeId;
    }

    @PostMapping("/recipes/{id}/comments")
    @RequirePermission(Permission.RECIPE_READ)
    public String comment(@PathVariable Long id, @RequestParam String body, @RequestParam(required = false) Long parentId,
                          @SessionAttribute("user") UserDto me) {
        comments.add(id, parentId, body, me);
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/comments/{id}/edit")
    @RequirePermission(Permission.RECIPE_READ)
    public String editComment(@PathVariable Long id, @RequestParam String body, @RequestParam Long recipeId, @SessionAttribute("user") UserDto me) {
        comments.edit(id, body, me);
        return "redirect:/recipes/" + recipeId;
    }

    @PostMapping("/comments/{id}/delete")
    @RequirePermission(Permission.RECIPE_READ)
    public String deleteComment(@PathVariable Long id, @RequestParam Long recipeId, @SessionAttribute("user") UserDto me) {
        comments.delete(id, me);
        return "redirect:/recipes/" + recipeId;
    }

    @GetMapping("/recipes/{id}/history")
    @RequirePermission(Permission.RECIPE_READ)
    public String history(@PathVariable Long id, @RequestParam(required = false) Integer a, @RequestParam(required = false) Integer b, Model model) {
        var versions = recipes.history(id);
        model.addAttribute("recipe", recipes.get(id));
        model.addAttribute("versions", versions);
        if (a != null && b != null) {
            model.addAttribute("diff", recipes.diff(id, a, b));
            model.addAttribute("a", a);
            model.addAttribute("b", b);
        }
        return "recipes/history";
    }

    @PostMapping("/recipes/{id}/versions/{no}/restore")
    @RequirePermission(Permission.RECIPE_WRITE)
    public String restore(@PathVariable Long id, @PathVariable int no, RedirectAttributes redirect) {
        recipes.restoreVersion(id, no);
        redirect.addFlashAttribute("success", "Version " + no + " was restored as a new version.");
        return "redirect:/recipes/" + id + "/history";
    }
}
