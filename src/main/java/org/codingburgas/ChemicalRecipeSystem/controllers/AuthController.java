package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

/** Login, logout and "my profile". */
@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        var user = userService.login(email, password);
        if (user.isEmpty()) {
            model.addAttribute("error", "Wrong email or password, or the account is deactivated.");
            return "login";
        }
        session.setAttribute("user", user.get());
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName, @RequestParam String email, @RequestParam String phone,
                                HttpSession session, RedirectAttributes redirect) {
        User me = (User) session.getAttribute("user");
        session.setAttribute("user", userService.updateProfile(me.getId(), fullName, email, phone));
        redirect.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile";
    }
}
