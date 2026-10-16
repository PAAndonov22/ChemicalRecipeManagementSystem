package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    public List<Order> latest() {
        return orders.findTop5ByOrderByOrderedAtDesc();
    }

    /**
     * Filters and sorts orders by week, date, day of week and hour.
     * Every filter is optional (null = don't filter).
     *
     * @param week   ISO week like "2026-W42" (the value of an HTML week input)
     * @param date   exact day
     * @param day    day of the week (MONDAY...SUNDAY)
     * @param hour   hour of the day 0-23
     * @param sortBy "week", "date", "day", "hour" or "total"
     */
    public List<Order> filterAndSort(String week, LocalDate date, DayOfWeek day, Integer hour, String sortBy, boolean descending) {
        Comparator<Order> comparator = switch (sortBy) {
            case "week" -> Comparator.comparing(Order::getWeek);
            case "day" -> Comparator.comparing(o -> o.getOrderedAt().getDayOfWeek());
            case "hour" -> Comparator.comparing(o -> o.getOrderedAt().getHour());
            case "total" -> Comparator.comparing(Order::getTotal);
            default -> Comparator.comparing(Order::getOrderedAt); // "date"
        };
        comparator = comparator.thenComparing(Order::getOrderedAt);

        return orders.findAll().stream()
            .filter(o -> week == null || week.isBlank() || o.getWeek().equals(week))
            .filter(o -> date == null || o.getOrderedAt().toLocalDate().equals(date))
            .filter(o -> day == null || o.getOrderedAt().getDayOfWeek() == day)
            .filter(o -> hour == null || o.getOrderedAt().getHour() == hour)
            .sorted(descending ? comparator.reversed() : comparator)
            .toList();
    }
}
