package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A customer order for a recipe ("orders" because ORDER is an SQL keyword). */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Recipe recipe;

    private String customerName;
    private int quantity;
    private double total;
    private LocalDateTime orderedAt;

    public Order() {
    }

    public Order(Recipe recipe, String customerName, int quantity, LocalDateTime orderedAt) {
        this.recipe = recipe;
        this.customerName = customerName;
        this.quantity = quantity;
        this.total = quantity * recipe.getPrice();
        this.orderedAt = orderedAt;
    }

    /** ISO week of the order, same format as an HTML week input, e.g. "2026-W07". */
    public String getWeek() {
        return isoWeek(orderedAt.toLocalDate());
    }

    public static String isoWeek(LocalDate date) {
        return "%d-W%02d".formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public String getCustomerName() { return customerName; }
    public int getQuantity() { return quantity; }
    public double getTotal() { return total; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
}
