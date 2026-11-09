package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.dto.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numbers for the dashboard charts. Cached; RecipeService evicts the cache when recipes change. */
@Service
@Transactional(readOnly = true)
public class StatsService {
    public record Dashboard(double revenue, long orders, long events, long recipes, long inReview,
                            List<String> monthLabels, List<Double> monthRevenue,
                            List<String> categoryLabels, List<Double> categoryRevenue,
                            List<String> statusLabels, List<Long> statusCounts) {
    }

    private final ReportService reports;
    private final OrderRepository orders;
    private final RecipeRepository recipes;

    public StatsService(ReportService reports, OrderRepository orders, RecipeRepository recipes) {
        this.reports = reports;
        this.orders = orders;
        this.recipes = recipes;
    }

    @Cacheable("stats")
    public Dashboard dashboard(int months, LocalDate today) {
        PeriodReport report = reports.monthly(months, today);
        List<String> monthLabels = report.rows().stream().map(r -> r.label()).toList();
        List<Double> monthRevenue = report.rows().stream().map(r -> r.totalRevenue()).toList();
        List<String> catLabels = new ArrayList<>();
        List<Double> catRevenue = new ArrayList<>();
        for (Object[] row : orders.revenueByCategory(report.from().atStartOfDay(), report.to().plusDays(1).atStartOfDay())) {
            catLabels.add((String) row[0]);
            catRevenue.add(((Number) row[1]).doubleValue());
        }
        List<String> statusLabels = new ArrayList<>();
        List<Long> statusCounts = new ArrayList<>();
        long total = 0;
        long inReview = 0;
        for (Object[] row : recipes.countByStatus()) {
            RecipeStatus status = (RecipeStatus) row[0];
            statusLabels.add(status.getLabel());
            statusCounts.add((Long) row[1]);
            total += (Long) row[1];
            if (status == RecipeStatus.IN_REVIEW) {
                inReview = (Long) row[1];
            }
        }
        return new Dashboard(report.totalRevenue(), report.totalOrders(), report.totalEvents(), total, inReview,
            monthLabels, monthRevenue, catLabels, catRevenue, statusLabels, statusCounts);
    }
}
