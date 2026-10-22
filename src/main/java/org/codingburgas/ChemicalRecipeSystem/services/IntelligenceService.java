package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper;
import org.codingburgas.ChemicalRecipeSystem.dto.OrderDto;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.ReportRow;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Methods for working with intelligent systems:
 * 1. Forecasting      - linear regression predicts next months' revenue
 * 2. Anomaly detection - z-score finds unusually large/small orders
 * 3. Recommendation   - Jaccard similarity finds recipes with similar ingredients
 * 4. Expert system    - IF-THEN rules classify the hazard level of a recipe
 */
@Service
@Transactional(readOnly = true)
public class IntelligenceService {
    public record Forecast(String month, double revenue) {}
    // results are DTOs too, so the view never touches an entity
    public record Anomaly(OrderDto order, double zScore) {}
    public record Recommendation(RecipeDto recipe, RecipeDto similar, double similarity) {}
    public record HazardAssessment(RecipeDto recipe, String level, int score, List<String> reasons) {}

    /** Knowledge base of the expert system: ingredient -> danger points and explanation. */
    private static final Map<String, Rule> RULES = Map.of(
        "ethanol", new Rule(3, "Ethanol is flammable"),
        "isopropyl alcohol", new Rule(3, "Isopropyl alcohol is highly flammable"),
        "hydrogen peroxide", new Rule(3, "Hydrogen peroxide is an oxidizer"),
        "menthol", new Rule(1, "Menthol irritates skin and eyes"),
        "zinc oxide", new Rule(1, "Zinc oxide dust is harmful if inhaled"),
        "citric acid", new Rule(1, "Citric acid irritates eyes")
    );

    private record Rule(int points, String reason) {}

    private final OrderRepository orders;
    private final RecipeRepository recipes;
    private final ReportService reportService;

    public IntelligenceService(OrderRepository orders, RecipeRepository recipes, ReportService reportService) {
        this.orders = orders;
        this.recipes = recipes;
        this.reportService = reportService;
    }

    // ---------- 1. Forecasting ----------

    /** Uses the revenue of the last 12 complete months to predict this and the next months. */
    public List<Forecast> forecastRevenue(int monthsAhead) {
        List<Double> history = reportService.monthly(12, LocalDate.now().minusMonths(1)).rows().stream()
            .map(ReportRow::totalRevenue)
            .toList();
        List<Double> predicted = linearRegression(history, monthsAhead);

        List<Forecast> result = new ArrayList<>();
        for (int i = 0; i < predicted.size(); i++) {
            result.add(new Forecast(YearMonth.now().plusMonths(i).toString(), predicted.get(i)));
        }
        return result;
    }

    /** Least squares line y = a + b*x through the history, continued for 'ahead' more points. */
    public static List<Double> linearRegression(List<Double> history, int ahead) {
        int n = history.size();
        double meanX = (n - 1) / 2.0;
        double meanY = history.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double top = 0;
        double bottom = 0;
        for (int x = 0; x < n; x++) {
            top += (x - meanX) * (history.get(x) - meanY);
            bottom += (x - meanX) * (x - meanX);
        }
        double slope = bottom == 0 ? 0 : top / bottom;
        double intercept = meanY - slope * meanX;

        List<Double> result = new ArrayList<>();
        for (int x = n; x < n + ahead; x++) {
            result.add(Math.max(0, intercept + slope * x));
        }
        return result;
    }

    // ---------- 2. Anomaly detection ----------

    public List<Anomaly> unusualOrders() {
        return findAnomalies(orders.findAll(), 2.5);
    }

    /** An order is unusual when its total is more than 'threshold' standard deviations away from the average. */
    public static List<Anomaly> findAnomalies(List<Order> list, double threshold) {
        double mean = list.stream().mapToDouble(Order::getTotal).average().orElse(0);
        double variance = list.stream().mapToDouble(o -> Math.pow(o.getTotal() - mean, 2)).average().orElse(0);
        double std = Math.sqrt(variance);
        if (std == 0) {
            return List.of();
        }
        return list.stream()
            .map(o -> new Anomaly(DtoMapper.toOrderDto(o), (o.getTotal() - mean) / std))
            .filter(a -> Math.abs(a.zScore()) > threshold)
            .sorted(Comparator.comparingDouble((Anomaly a) -> Math.abs(a.zScore())).reversed())
            .toList();
    }

    // ---------- 3. Recommendation ----------

    /** For every recipe: the most similar other recipe by ingredients. */
    public List<Recommendation> recommendations() {
        List<Recipe> all = recipes.findAll();
        List<Recommendation> result = new ArrayList<>();
        for (Recipe recipe : all) {
            all.stream()
                .filter(other -> !other.getId().equals(recipe.getId()))
                .max(Comparator.comparingDouble((Recipe other) -> jaccard(recipe.ingredientSet(), other.ingredientSet())))
                .ifPresent(best -> result.add(new Recommendation(DtoMapper.toRecipeDto(recipe), DtoMapper.toRecipeDto(best),
                    jaccard(recipe.ingredientSet(), best.ingredientSet()))));
        }
        return result;
    }

    /** Jaccard similarity = common ingredients / all different ingredients (0..1). */
    public static double jaccard(Set<String> a, Set<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        return (double) common.size() / union.size();
    }

    // ---------- 4. Expert system ----------

    public List<HazardAssessment> hazardAssessments() {
        return recipes.findAll().stream()
            .map(IntelligenceService::assessHazard)
            .sorted(Comparator.comparingInt(HazardAssessment::score).reversed())
            .toList();
    }

    /** Applies the IF-THEN rules to the ingredients and decides LOW / MEDIUM / HIGH. */
    public static HazardAssessment assessHazard(Recipe recipe) {
        Set<String> ingredients = recipe.ingredientSet();
        int score = 0;
        List<String> reasons = new ArrayList<>();
        for (String ingredient : ingredients) {
            Rule rule = RULES.get(ingredient);
            if (rule != null) {
                score += rule.points();
                reasons.add(rule.reason());
            }
        }
        boolean flammable = ingredients.contains("ethanol") || ingredients.contains("isopropyl alcohol");
        if (flammable && ingredients.contains("hydrogen peroxide")) {
            score += 4;
            reasons.add("Oxidizer mixed with a flammable solvent");
        }
        String level = score >= 6 ? "HIGH" : score >= 3 ? "MEDIUM" : "LOW";
        if (reasons.isEmpty()) {
            reasons.add("No hazardous ingredients found");
        }
        return new HazardAssessment(DtoMapper.toRecipeDto(recipe), level, score, reasons);
    }
}
