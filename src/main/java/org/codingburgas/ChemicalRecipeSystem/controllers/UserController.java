package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin only (see WebConfig): search, create, edit, deactivate users and manage roles. */
@Controller
@RequestMapping("/admin/users")
public class UserController {
    private static final int PAGE_SIZE = 10;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String role,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "createdAt") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Role roleFilter = role.isBlank() ? null : Role.valueOf(role);
        Boolean activeFilter = status.isBlank() ? null : status.equals("active");

        model.addAttribute("users", userService.search(q, roleFilter, activeFilter, sort, dir, page, PAGE_SIZE));
        model.addAttribute("q", q);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("roles", Role.values());
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String create(@RequestParam String fullName, @RequestParam String email, @RequestParam String phone,
                         @RequestParam String password, @RequestParam Role role, RedirectAttributes redirect) {
        userService.create(fullName, email, phone, password, role);
        redirect.addFlashAttribute("success", "User " + email + " created.");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.get(id));
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String fullName, @RequestParam String email,
                         @RequestParam String phone, RedirectAttributes redirect) {
        userService.updateProfile(id, fullName, email, phone);
        redirect.addFlashAttribute("success", "Profile saved.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role, @SessionAttribute("user") User admin, RedirectAttributes redirect) {
        userService.changeRole(id, role, admin);
        redirect.addFlashAttribute("success", "Role changed to " + role + ".");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, @SessionAttribute("user") User admin, RedirectAttributes redirect) {
        userService.setActive(id, active, admin);
        redirect.addFlashAttribute("success", active ? "User activated." : "User deactivated.");
        return "redirect:/admin/users/" + id;
    }

    /** New users today / last week / month / year. */
    @GetMapping("/registrations")
    public String registrations(@RequestParam(defaultValue = "week") String period, Model model) {
        var perMonth = userService.registrationsPerMonth();
        model.addAttribute("period", period);
        model.addAttribute("counts", userService.registrationCounts());
        model.addAttribute("newUsers", userService.registeredSince(period));
        model.addAttribute("perMonth", perMonth);
        model.addAttribute("maxPerMonth", Math.max(1, perMonth.values().stream().mapToLong(Long::longValue).max().orElse(1)));
        return "users/registrations";
    }
}
