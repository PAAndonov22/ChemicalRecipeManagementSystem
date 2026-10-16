package org.codingburgas.ChemicalRecipeSystem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.EventType;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.EventRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Fills an empty database with demo data on startup. */
@Component
public class MockData implements CommandLineRunner {
    private static final String[] FIRST = {"Ivan", "Maria", "Georgi", "Elena", "Petar", "Nikol", "Dimitar", "Viktoria", "Stefan", "Yana"};
    private static final String[] LAST = {"Ivanov", "Petrova", "Georgiev", "Dimitrova", "Stoyanov", "Koleva", "Todorov", "Nikolova"};
    private static final String[] CUSTOMERS = {"PharmaBG", "Burgas Lab", "MedChem Ltd", "Sofia Clinic", "VetCare", "BioLine", "AquaPure"};

    private final UserService userService;
    private final UserRepository users;
    private final RecipeRepository recipes;
    private final OrderRepository orders;
    private final EventRepository events;
    private final Random random = new Random(42);

    public MockData(UserService userService, UserRepository users, RecipeRepository recipes, OrderRepository orders, EventRepository events) {
        this.userService = userService;
        this.users = users;
        this.recipes = recipes;
        this.orders = orders;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        seedUsers();
        seedOrders(seedRecipes());
        seedEvents();
    }

    private void seedUsers() {
        userService.create("Admin", "admin@crms.local", "0888000001", "Admin123!", Role.ADMIN);
        userService.create("Chemist", "chemist@crms.local", "0888000002", "Pass123!", Role.CHEMIST);
        userService.create("Technician", "technician@crms.local", "0888000003", "Pass123!", Role.TECHNICIAN);
        userService.create("Vet", "vet@crms.local", "0888000004", "Pass123!", Role.VET);
        userService.create("Reception", "reception@crms.local", "0888000005", "Pass123!", Role.RECEPTION);

        // 45 more users registered at different times in the last ~13 months
        Role[] roles = Role.values();
        for (int i = 0; i < 45; i++) {
            String name = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
            User user = userService.create(name, "user" + i + "@crms.local", "0899" + (100000 + i), "Pass123!", roles[random.nextInt(roles.length)]);
            int daysAgo = i < 3 ? 0 : i < 7 ? random.nextInt(7) : random.nextInt(400);
            user.setCreatedAt(LocalDateTime.now().minusDays(daysAgo).minusMinutes(random.nextInt(600)));
            user.setActive(i % 9 != 0);
            users.save(user);
        }
    }

    private List<Recipe> seedRecipes() {
        return recipes.saveAll(List.of(
            recipe("RX-1001", "Saline Nasal Rinse", "Distilled Water, Sodium Chloride, Sodium Bicarbonate", 4.5),
            recipe("RX-1002", "Cooling Gel Base", "Distilled Water, Glycerin, Menthol, Ethanol", 9.9),
            recipe("RX-1003", "Mineral Suspension", "Distilled Water, Zinc Oxide, Magnesium Hydroxide", 7.2),
            recipe("RX-1004", "Cough Syrup Base", "Distilled Water, Glycerin, Citric Acid, Potassium Sorbate", 6.4),
            recipe("RX-1005", "Antiseptic Spray", "Isopropyl Alcohol, Ethanol, Distilled Water", 8.1),
            recipe("RX-1006", "Wound Wash", "Distilled Water, Hydrogen Peroxide, Sodium Chloride", 5.3),
            recipe("RX-1007", "Surface Disinfectant", "Hydrogen Peroxide, Ethanol, EDTA", 11.0),
            recipe("RX-1008", "Electrolyte Drink", "Distilled Water, Sodium Chloride, Sodium Citrate, Citric Acid", 3.9),
            recipe("RX-1009", "Aloe Recovery Spray", "Distilled Water, Aloe Vera Extract, Glycerin, Potassium Sorbate", 7.7),
            recipe("RX-1010", "Barrier Cream", "Zinc Oxide, Dimethicone, Glycerin", 12.5)
        ));
    }

    private void seedOrders(List<Recipe> recipeList) {
        for (int i = 0; i < 320; i++) {
            LocalDateTime time = LocalDate.now().minusDays(random.nextInt(420))
                .atTime(8 + random.nextInt(11), random.nextInt(60));
            int quantity = 1 + random.nextInt(25);
            if (i % 97 == 0) {
                quantity = 300; // a few unusual orders for the anomaly detection
            }
            orders.save(new Order(recipeList.get(random.nextInt(recipeList.size())), CUSTOMERS[random.nextInt(CUSTOMERS.length)], quantity, time));
        }
    }

    private void seedEvents() {
        EventType[] types = EventType.values();
        String[] topics = {"Lab Safety", "Formulation Basics", "Green Chemistry", "Quality Control", "New Products"};
        for (int monthsAgo = 13; monthsAgo >= 0; monthsAgo--) {
            int count = 2 + random.nextInt(3);
            for (int j = 0; j < count; j++) {
                LocalDate date = LocalDate.now().minusMonths(monthsAgo).withDayOfMonth(1 + random.nextInt(27));
                if (date.isAfter(LocalDate.now())) {
                    date = LocalDate.now();
                }
                EventType type = types[random.nextInt(types.length)];
                String title = topics[random.nextInt(topics.length)] + " " + type.name().replace('_', ' ').toLowerCase();
                events.save(new Event(title, type, date, 10 + random.nextInt(60), 15 + random.nextInt(4) * 10));
            }
        }
    }

    private Recipe recipe(String code, String name, String ingredients, double price) {
        Recipe recipe = new Recipe();
        recipe.setCode(code);
        recipe.setName(name);
        recipe.setDescription(name + " reference formulation.");
        recipe.setIngredients(ingredients);
        recipe.setPrice(price);
        return recipe;
    }
}
