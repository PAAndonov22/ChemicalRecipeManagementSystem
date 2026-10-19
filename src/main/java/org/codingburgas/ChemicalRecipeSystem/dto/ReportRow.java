package org.codingburgas.ChemicalRecipeSystem.dto;

/** DTO: one line of a report - a group (day / week / month / year) and its totals. */
public record ReportRow(String label, long orders, double orderRevenue, long events, double eventRevenue) {
    public double totalRevenue() {
        return orderRevenue + eventRevenue;
    }
}
