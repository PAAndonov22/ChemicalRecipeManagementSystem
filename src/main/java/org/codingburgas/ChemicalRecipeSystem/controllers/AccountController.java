package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.dto.PreferencesDto;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.models.Theme;
import org.codingburgas.ChemicalRecipeSystem.services.CollectionService;
import org.codingburgas.ChemicalRecipeSystem.services.NotificationService;
import org.codingburgas.ChemicalRecipeSystem.services.PreferencesService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Everything personal: favorites + lists, notifications, settings. Always works on the logged-in user's own data. */
@Controller
public class AccountController {
    private final CollectionService collections;
    private final NotificationService notifications;
    private final PreferencesService preferences;

    public AccountController(CollectionService collections, NotificationService notifications, PreferencesService preferences) {
        this.collections = collections;
        this.notifications = notifications;
        this.preferences = preferences;
    }

    @GetMapping("/collections")
    public String lists(@SessionAttribute("user") UserDto me, Model model) {
        model.addAttribute("lists", collections.list(me.getId()));
        return "collections";
    }

    @PostMapping("/collections")
    public String createList(@RequestParam String name, @SessionAttribute("user") UserDto me, RedirectAttributes redirect) {
        collections.create(me.getId(), name);
        redirect.addFlashAttribute("success", "List created.");
        return "redirect:/collections";
    }

    @PostMapping("/collections/{id}/add")
    public String addToList(@PathVariable Long id, @RequestParam Long recipeId, @SessionAttribute("user") UserDto me, RedirectAttributes redirect) {
        boolean added = collections.add(me.getId(), id, recipeId);
        redirect.addFlashAttribute("success", added ? "Added to the list." : "It is already in that list.");
        return "redirect:/recipes/" + recipeId;
    }

    @PostMapping("/collections/{id}/remove")
    public String removeFromList(@PathVariable Long id, @RequestParam Long recipeId, @SessionAttribute("user") UserDto me) {
        collections.remove(me.getId(), id, recipeId);
        return "redirect:/collections";
    }

    @PostMapping("/recipes/{id}/favorite")
    public String favorite(@PathVariable Long id, @SessionAttribute("user") UserDto me, RedirectAttributes redirect) {
        redirect.addFlashAttribute("success", collections.toggleFavorite(me.getId(), id) ? "Added to favorites." : "Removed from favorites.");
        return "redirect:/recipes/" + id;
    }

    @GetMapping("/notifications")
    public String notificationList(@RequestParam(defaultValue = "0") int page, @SessionAttribute("user") UserDto me, Model model) {
        var items = notifications.page(me.getId(), page, 15);
        model.addAttribute("items", items);
        model.addAttribute("pager", items);
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String read(@PathVariable Long id, @SessionAttribute("user") UserDto me) {
        notifications.markRead(id, me.getId());
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String readAll(@SessionAttribute("user") UserDto me) {
        notifications.markAllRead(me.getId());
        return "redirect:/notifications";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("themes", Theme.values());
        model.addAttribute("sizes", java.util.List.of(10, 20, 50));
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Theme theme, @RequestParam int pageSize, @RequestParam(defaultValue = "false") boolean notifyStatus,
                               @RequestParam(defaultValue = "false") boolean notifyComments, @SessionAttribute("user") UserDto me,
                               HttpSession session, RedirectAttributes redirect) {
        PreferencesDto saved = preferences.save(me.getId(), theme, pageSize, notifyStatus, notifyComments);
        session.setAttribute("prefs", saved);
        redirect.addFlashAttribute("success", "Settings saved.");
        return "redirect:/settings";
    }
}
