package org.codingburgas.ChemicalRecipeSystem.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.codingburgas.ChemicalRecipeSystem.models.EventType;

/** DTO: everything a report page (and the PDF) needs for one period. */
public record PeriodReport(
    LocalDate from,
    LocalDate to,
    String groupBy,
    List<ReportRow> rows,
    List<EventDto> events,
    Map<EventType, Long> eventCountByType,
    Map<EventType, Double> eventRevenueByType
) {
    public long totalOrders() { return rows.stream().mapToLong(ReportRow::orders).sum(); }
    public double orderRevenue() { return rows.stream().mapToDouble(ReportRow::orderRevenue).sum(); }
    public long totalEvents() { return rows.stream().mapToLong(ReportRow::events).sum(); }
    public double eventRevenue() { return rows.stream().mapToDouble(ReportRow::eventRevenue).sum(); }
    public double totalRevenue() { return orderRevenue() + eventRevenue(); }

    /** Biggest row value - used to scale the bar chart. */
    public double maxRowRevenue() {
        return rows.stream().mapToDouble(ReportRow::totalRevenue).max().orElse(1);
    }
}
