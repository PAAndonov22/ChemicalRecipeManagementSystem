package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A customer order for a recipe ("orders" because ORDER is an SQL keyword). */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    /** Many-to-One association: many orders can be for the same recipe. */
    @ManyToOne(optional = false)
    private Recipe recipe;

    /** Many-to-One, the owning side of Customer.orders (column customer_id). */
    @ManyToOne(optional = false)
    private Customer customer;

    /** Many-to-One association: the employee who entered the order (optional). */
    @ManyToOne
    private User createdBy;

    private int quantity;
    private double total;
    private LocalDateTime orderedAt;

    protected Order() {
    }

    public Order(Recipe recipe, Customer customer, User createdBy, int quantity, LocalDateTime orderedAt) {
        this.recipe = recipe;
        this.customer = customer;
        this.createdBy = createdBy;
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

    public Recipe getRecipe() { return recipe; }
    public Customer getCustomer() { return customer; }
    public User getCreatedBy() { return createdBy; }
    public int getQuantity() { return quantity; }
    public double getTotal() { return total; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
}
