package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.time.LocalDate;

import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.codingburgas.ChemicalRecipeSystem.services.ReportService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final ReportService reportService;
    private final OrderService orderService;
    private final UserService userService;

    public HomeController(ReportService reportService, OrderService orderService, UserService userService) {
        this.reportService = reportService;
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("thisMonth", reportService.monthly(1, LocalDate.now()));
        model.addAttribute("latestOrders", orderService.latest());
        model.addAttribute("registrations", userService.registrationCounts());
        return "dashboard";
    }
}
