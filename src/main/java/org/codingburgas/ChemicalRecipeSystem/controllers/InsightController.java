package org.codingburgas.ChemicalRecipeSystem.controllers;

import org.codingburgas.ChemicalRecipeSystem.services.IntelligenceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Shows the results of the intelligent methods. */
@Controller
public class InsightController {
    private final IntelligenceService intelligenceService;

    public InsightController(IntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @GetMapping("/insights")
    public String insights(Model model) {
        model.addAttribute("forecast", intelligenceService.forecastRevenue(3));
        model.addAttribute("anomalies", intelligenceService.unusualOrders());
        model.addAttribute("recommendations", intelligenceService.recommendations());
        model.addAttribute("hazards", intelligenceService.hazardAssessments());
        return "insights";
    }
}
