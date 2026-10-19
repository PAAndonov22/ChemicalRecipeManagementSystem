package org.codingburgas.ChemicalRecipeSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Customer;
import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.repositories.OrderRepository;
import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private final Recipe recipe = new Recipe();
    private final Customer customer = new Customer("Test Ltd", "Tester", null, null, "Burgas");
    // Friday 2026-10-16, ISO week 2026-W42
    private final Order morning = new Order(recipe, customer, null, 1, LocalDateTime.of(2026, 10, 16, 9, 0));
    private final Order evening = new Order(recipe, customer, null, 1, LocalDateTime.of(2026, 10, 16, 18, 0));
    private final Order monday = new Order(recipe, customer, null, 1, LocalDateTime.of(2026, 10, 12, 9, 30));
    private final Order lastMonth = new Order(recipe, customer, null, 1, LocalDateTime.of(2026, 9, 1, 12, 0));
    private final OrderService service;

    OrderServiceTest() {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findAll()).thenReturn(List.of(evening, lastMonth, morning, monday));
        service = new OrderService(repository);
    }

    @Test
    void filtersByWeekAndSortsByDate() {
        assertEquals(List.of(monday, morning, evening), service.filterAndSort("2026-W42", null, null, null, "date", false));
    }

    @Test
    void filtersByDateDayAndHour() {
        assertEquals(List.of(morning, evening), service.filterAndSort(null, LocalDate.of(2026, 10, 16), null, null, "date", false));
        assertEquals(List.of(monday), service.filterAndSort(null, null, DayOfWeek.MONDAY, null, "date", false));
        assertEquals(List.of(monday, morning), service.filterAndSort(null, null, null, 9, "date", false));
    }

    @Test
    void sortsByHourDescending() {
        assertEquals(evening, service.filterAndSort(null, null, null, null, "hour", true).getFirst());
    }
}
