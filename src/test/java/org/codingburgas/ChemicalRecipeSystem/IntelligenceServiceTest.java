package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        Recipe recipe = new Recipe();
        recipe.setIngredients("Ethanol, Hydrogen Peroxide, Water");
        assertEquals("HIGH", IntelligenceService.assessHazard(recipe).level());

        recipe.setIngredients("Water, Glycerin");
        assertEquals("LOW", IntelligenceService.assessHazard(recipe).level());
    }

    @Test
    void findsTheOneHugeOrder() {
        Recipe recipe = new Recipe();
        recipe.setPrice(10);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            orders.add(new Order(recipe, "A", 5, LocalDateTime.now()));
        }
        orders.add(new Order(recipe, "B", 500, LocalDateTime.now()));
        assertEquals(1, IntelligenceService.findAnomalies(orders, 2.5).size());
    }
}
