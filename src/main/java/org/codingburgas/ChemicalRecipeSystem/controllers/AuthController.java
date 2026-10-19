package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.dto.ProfileForm;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
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
    public String updateProfile(@ModelAttribute ProfileForm form, @SessionAttribute("user") UserDto me,
                                HttpSession session, RedirectAttributes redirect) {
        session.setAttribute("user", userService.updateProfile(me.getId(), form));
        redirect.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile";
    }
}
