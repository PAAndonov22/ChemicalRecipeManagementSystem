package org.codingburgas.ChemicalRecipeSystem.dto;

import java.time.LocalDateTime;

/** DTO: an order flattened for tables - names instead of the Recipe / Customer / User entities. */
public class OrderDto {
    private final Long id;
    private final LocalDateTime orderedAt;
    private final String week;
    private final String recipeCode;
    private final String recipeName;
    private final String customerName;
    private final String createdByName;
    private final int quantity;
    private final double total;

    public OrderDto(Long id, LocalDateTime orderedAt, String week, String recipeCode, String recipeName,
                    String customerName, String createdByName, int quantity, double total) {
        this.id = id;
        this.orderedAt = orderedAt;
        this.week = week;
        this.recipeCode = recipeCode;
        this.recipeName = recipeName;
        this.customerName = customerName;
        this.createdByName = createdByName;
        this.quantity = quantity;
        this.total = total;
    }

    public Long getId() { return id; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public String getWeek() { return week; }
    public String getRecipeCode() { return recipeCode; }
    public String getRecipeName() { return recipeName; }
    public String getCustomerName() { return customerName; }
    public String getCreatedByName() { return createdByName; }
    public int getQuantity() { return quantity; }
    public double getTotal() { return total; }
}
