package org.codingburgas.ChemicalRecipeSystem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.codingburgas.ChemicalRecipeSystem.dto.RecipeForm;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.models.Category;
import org.codingburgas.ChemicalRecipeSystem.models.Customer;
import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.EventType;
import org.codingburgas.ChemicalRecipeSystem.models.Ingredient;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.CategoryRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.CustomerRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.EventRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.IngredientRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.codingburgas.ChemicalRecipeSystem.services.RecipeService;
import org.codingburgas.ChemicalRecipeSystem.services.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Fills an empty database with demo data on startup. */
@Component
public class MockData implements CommandLineRunner {
    private static final String[] FIRST = {"Ivan", "Maria", "Georgi", "Elena", "Petar", "Nikol", "Dimitar", "Viktoria", "Stefan", "Yana"};
    private static final String[] LAST = {"Ivanov", "Petrova", "Georgiev", "Dimitrova", "Stoyanov", "Koleva", "Todorov", "Nikolova"};
    private static final String[][] CUSTOMERS = {
        {"PharmaBG", "Sofia"}, {"Burgas Lab", "Burgas"}, {"MedChem Ltd", "Plovdiv"}, {"Sofia Clinic", "Sofia"},
        {"VetCare", "Varna"}, {"BioLine", "Burgas"}, {"AquaPure", "Ruse"}
    };

    private final UserService userService;
    private final RecipeService recipeService;
    private final UserRepository users;
    private final RecipeRepository recipes;
    private final CategoryRepository categories;
    private final IngredientRepository ingredients;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final EventRepository events;
    private final Random random = new Random(42);

    public MockData(UserService userService, RecipeService recipeService, UserRepository users, RecipeRepository recipes,
                    CategoryRepository categories, IngredientRepository ingredients, CustomerRepository customers,
                    OrderRepository orders, EventRepository events) {
        this.userService = userService;
        this.recipeService = recipeService;
        this.users = users;
        this.recipes = recipes;
        this.categories = categories;
        this.ingredients = ingredients;
        this.customers = customers;
        this.orders = orders;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        seedUsers();
        seedCategories();
        seedIngredients();
        seedRecipes();
        seedCustomers();
        seedOrders();
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
            UserDto created = userService.create(name, "user" + i + "@crms.local", "0899" + (100000 + i), "Pass123!", roles[random.nextInt(roles.length)]);
            User user = users.findById(created.getId()).orElseThrow();
            int daysAgo = i < 3 ? 0 : i < 7 ? random.nextInt(7) : random.nextInt(400);
            user.setCreatedAt(LocalDateTime.now().minusDays(daysAgo).minusMinutes(random.nextInt(600)));
            user.setActive(i % 9 != 0);
            users.save(user);
        }
    }

    private void seedCategories() {
        categories.saveAll(List.of(
            new Category("Pharmaceutical", "Medicines and medical preparations"),
            new Category("Disinfectant", "Products that kill germs on skin or surfaces"),
            new Category("Cosmetic", "Skin care products"),
            new Category("Veterinary", "Products that are also safe for animals"),
            new Category("Nutritional", "Drinks and supplements")
        ));
    }

    private void seedIngredients() {
        ingredients.saveAll(List.of(
            new Ingredient("Distilled Water", "7732-18-5"),
            new Ingredient("Sodium Chloride", "7647-14-5"),
            new Ingredient("Sodium Bicarbonate", "144-55-8"),
            new Ingredient("Glycerin", "56-81-5"),
            new Ingredient("Menthol", "89-78-1"),
            new Ingredient("Ethanol", "64-17-5"),
            new Ingredient("Zinc Oxide", "1314-13-2"),
            new Ingredient("Magnesium Hydroxide", "1309-42-8"),
            new Ingredient("Citric Acid", "77-92-9"),
            new Ingredient("Potassium Sorbate", "24634-61-5"),
            new Ingredient("Isopropyl Alcohol", "67-63-0"),
            new Ingredient("Hydrogen Peroxide", "7722-84-1"),
            new Ingredient("EDTA", "60-00-4"),
            new Ingredient("Sodium Citrate", "68-04-2"),
            new Ingredient("Aloe Vera Extract", null),
            new Ingredient("Dimethicone", "63148-62-9")
        ));
    }

    /** Recipes are saved through RecipeService - the same code the recipe form uses. Quantities are per litre. */
    private void seedRecipes() {
        recipe("RX-1001", "Saline Nasal Rinse", 4.5, "Pharmaceutical", SignalWord.NONE, "Not classified",
            "Distilled Water, 990, ml", "Sodium Chloride, 9, g", "Sodium Bicarbonate, 1, g");
        recipe("RX-1002", "Cooling Gel Base", 9.9, "Cosmetic, Pharmaceutical", SignalWord.WARNING, "Flammable liquid, Cat. 3",
            "Distilled Water, 800, ml", "Glycerin, 100, ml", "Menthol, 5, g", "Ethanol, 95, ml");
        recipe("RX-1003", "Mineral Suspension", 7.2, "Pharmaceutical", SignalWord.WARNING, "Harmful if inhaled (dust)",
            "Distilled Water, 900, ml", "Zinc Oxide, 60, g", "Magnesium Hydroxide, 40, g");
        recipe("RX-1004", "Cough Syrup Base", 6.4, "Pharmaceutical", SignalWord.WARNING, "Eye irritation, Cat. 2",
            "Distilled Water, 700, ml", "Glycerin, 280, ml", "Citric Acid, 15, g", "Potassium Sorbate, 1, g");
        recipe("RX-1005", "Antiseptic Spray", 8.1, "Disinfectant", SignalWord.DANGER, "Flammable liquid, Cat. 2",
            "Isopropyl Alcohol, 600, ml", "Ethanol, 300, ml", "Distilled Water, 100, ml");
        recipe("RX-1006", "Wound Wash", 5.3, "Pharmaceutical, Veterinary", SignalWord.WARNING, "Eye irritation, Cat. 2",
            "Distilled Water, 960, ml", "Hydrogen Peroxide, 30, ml", "Sodium Chloride, 9, g");
        recipe("RX-1007", "Surface Disinfectant", 11.0, "Disinfectant", SignalWord.DANGER, "Flammable liquid, Cat. 2; Oxidizer",
            "Hydrogen Peroxide, 60, ml", "Ethanol, 700, ml", "EDTA, 2, g");
        recipe("RX-1008", "Electrolyte Drink", 3.9, "Nutritional, Veterinary", SignalWord.NONE, "Not classified",
            "Distilled Water, 990, ml", "Sodium Chloride, 3, g", "Sodium Citrate, 3, g", "Citric Acid, 2, g");
        recipe("RX-1009", "Aloe Recovery Spray", 7.7, "Cosmetic, Veterinary", SignalWord.NONE, "Not classified",
            "Distilled Water, 850, ml", "Aloe Vera Extract, 120, ml", "Glycerin, 28, ml", "Potassium Sorbate, 2, g");
        recipe("RX-1010", "Barrier Cream", 12.5, "Cosmetic, Pharmaceutical", SignalWord.WARNING, "Harmful if inhaled (dust)",
            "Zinc Oxide, 150, g", "Dimethicone, 100, ml", "Glycerin, 50, ml");
    }

    private void seedCustomers() {
        for (int i = 0; i < CUSTOMERS.length; i++) {
            String company = CUSTOMERS[i][0];
            String contact = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
            String email = company.toLowerCase().replaceAll("[^a-z]", "") + "@customers.crms.local";
            customers.save(new Customer(company, contact, email, "0877" + (200000 + i), CUSTOMERS[i][1]));
        }
    }

    private void seedOrders() {
        List<Recipe> recipeList = recipes.findAll();
        List<Customer> customerList = customers.findAll();
        List<User> staff = users.findAll().stream()
            .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.RECEPTION)
            .toList();
        for (int i = 0; i < 320; i++) {
            LocalDateTime time = LocalDate.now().minusDays(random.nextInt(420))
                .atTime(8 + random.nextInt(11), random.nextInt(60));
            int quantity = 1 + random.nextInt(25);
            if (i % 97 == 0) {
                quantity = 300; // a few unusual orders for the anomaly detection
            }
            orders.save(new Order(pick(recipeList), pick(customerList), pick(staff), quantity, time));
        }
    }

    private void seedEvents() {
        List<User> organizers = users.findAll().stream()
            .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.CHEMIST)
            .toList();
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
                events.save(new Event(title, type, date, 10 + random.nextInt(60), 15 + random.nextInt(4) * 10, pick(organizers)));
            }
        }
    }

    private void recipe(String code, String name, double price, String categoryNames, SignalWord signalWord,
                        String hazardClass, String... ingredientLines) {
        RecipeForm form = new RecipeForm();
        form.setCode(code);
        form.setName(name);
        form.setDescription(name + " reference formulation.");
        form.setPrice(price);
        form.setIngredients(String.join("\n", ingredientLines));
        form.setCategoryIds(Arrays.stream(categoryNames.split(","))
            .map(c -> categories.findByName(c.trim()).orElseThrow().getId())
            .toList());
        form.setHazardClass(hazardClass);
        form.setSignalWord(signalWord);
        switch (signalWord) {
            case DANGER -> {
                form.setStorageConditions("Below 25 °C, away from heat, sparks and open flames. Keep tightly closed.");
                form.setProtectiveEquipment("Safety goggles, nitrile gloves, lab coat.");
            }
            case WARNING -> {
                form.setStorageConditions("Cool, dry place, tightly closed.");
                form.setProtectiveEquipment("Safety goggles and gloves.");
            }
            default -> {
                form.setStorageConditions("Room temperature.");
                form.setProtectiveEquipment("No special equipment needed.");
            }
        }
        recipeService.save(form);
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}
