package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Runs for every controller: gives each view the logged in user and shows service errors nicely. */
@ControllerAdvice
public class GlobalControllerAdvice {
    @ModelAttribute("currentUser")
    public User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /** Services throw IllegalArgumentException for bad input -> go back to the same page with the message. */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadInput(IllegalArgumentException exception, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", exception.getMessage());
        String previousPage = request.getHeader("Referer");
        return "redirect:" + (previousPage != null ? previousPage : "/");
    }
}
