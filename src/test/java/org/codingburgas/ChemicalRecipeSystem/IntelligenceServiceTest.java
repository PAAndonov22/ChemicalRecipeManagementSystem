package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.models.Customer;
import org.codingburgas.ChemicalRecipeSystem.models.Ingredient;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.services.IntelligenceService;
import org.junit.jupiter.api.Test;

/** Plain unit tests - no Spring, no database. */
class IntelligenceServiceTest {
    @Test
    void linearRegressionContinuesTheTrend() {
        List<Double> prediction = IntelligenceService.linearRegression(List.of(10.0, 20.0, 30.0), 2);
        assertEquals(40.0, prediction.get(0), 0.001);
        assertEquals(50.0, prediction.get(1), 0.001);
    }

    @Test
    void jaccardSimilarity() {
        assertEquals(0.5, IntelligenceService.jaccard(Set.of("a", "b"), Set.of("b", "c", "a", "d")), 0.001);
        assertEquals(0.0, IntelligenceService.jaccard(Set.of(), Set.of()), 0.001);
    }

    @Test
    void expertSystemFlagsOxidizerWithSolvent() {
        Recipe dangerous = new Recipe();
        dangerous.addIngredient(new Ingredient("Ethanol", "64-17-5"), 500, "ml");
        dangerous.addIngredient(new Ingredient("Hydrogen Peroxide", "7722-84-1"), 50, "ml");
        dangerous.addIngredient(new Ingredient("Water", null), 450, "ml");
        assertEquals("HIGH", IntelligenceService.assessHazard(dangerous).level());

        Recipe safe = new Recipe();
        safe.addIngredient(new Ingredient("Water", null), 900, "ml");
        safe.addIngredient(new Ingredient("Glycerin", null), 100, "ml");
        assertEquals("LOW", IntelligenceService.assessHazard(safe).level());
    }

    @Test
    void findsTheOneHugeOrder() {
        Recipe recipe = new Recipe();
        recipe.setPrice(10);
        Customer a = new Customer("A Ltd", "Ann", null, null, "Burgas");
        Customer b = new Customer("B Ltd", "Bob", null, null, "Sofia");
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            orders.add(new Order(recipe, a, null, 5, LocalDateTime.now()));
        }
        orders.add(new Order(recipe, b, null, 500, LocalDateTime.now()));
        var anomalies = IntelligenceService.findAnomalies(orders, 2.5);
        assertEquals(1, anomalies.size());
        assertEquals("B Ltd", anomalies.getFirst().order().getCustomerName());
    }
}
