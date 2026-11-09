package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.codingburgas.ChemicalRecipeSystem.services.ReportService;
import org.codingburgas.ChemicalRecipeSystem.services.StatsService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {
    private final ReportService reportService;
    private final OrderService orderService;
    private final UserService userService;
    private final StatsService statsService;

    public HomeController(ReportService reportService, OrderService orderService, UserService userService, StatsService statsService) {
        this.reportService = reportService;
        this.orderService = orderService;
        this.userService = userService;
        this.statsService = statsService;
    }

    /** KPIs and charts for the last 1, 6 or 12 months; the numbers come from JPA queries and are cached. */
    @GetMapping("/")
    public String dashboard(@RequestParam(defaultValue = "6") int months, Model model) {
        int m = months == 1 || months == 12 ? months : 6;
        var stats = statsService.dashboard(m, LocalDate.now());
        model.addAttribute("months", m);
        model.addAttribute("stats", stats);
        model.addAttribute("chartData", Map.of(
            "monthLabels", stats.monthLabels(), "monthRevenue", stats.monthRevenue(),
            "categoryLabels", stats.categoryLabels(), "categoryRevenue", stats.categoryRevenue(),
            "statusLabels", stats.statusLabels(), "statusCounts", stats.statusCounts()));
        model.addAttribute("latestOrders", orderService.latest());
        model.addAttribute("registrations", userService.registrationCounts());
        return "dashboard";
    }
}
