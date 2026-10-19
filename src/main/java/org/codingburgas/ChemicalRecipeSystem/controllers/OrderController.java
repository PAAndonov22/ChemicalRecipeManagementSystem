package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.dto.OrderDto;
import org.codingburgas.ChemicalRecipeSystem.services.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String list(@RequestParam(required = false) String week,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) DayOfWeek day,
                       @RequestParam(required = false) Integer hour,
                       @RequestParam(defaultValue = "date") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        List<OrderDto> orders = orderService.search(week, date, day, hour, sort, dir.equals("desc"));
        model.addAttribute("orders", orders);
        model.addAttribute("total", orders.stream().mapToDouble(OrderDto::getTotal).sum());
        model.addAttribute("week", week);
        model.addAttribute("date", date);
        model.addAttribute("day", day);
        model.addAttribute("hour", hour);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("days", DayOfWeek.values());
        return "orders";
    }
}
