package org.codingburgas.ChemicalRecipeSystem.dto;

import java.time.LocalDate;

import org.codingburgas.ChemicalRecipeSystem.models.EventType;

/** DTO: an event as shown in reports and in the PDF. */
public class EventDto {
    private final Long id;
    private final String title;
    private final EventType type;
    private final LocalDate date;
    private final int ticketsSold;
    private final double ticketPrice;
    private final String organizerName;

    public EventDto(Long id, String title, EventType type, LocalDate date, int ticketsSold, double ticketPrice, String organizerName) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.date = date;
        this.ticketsSold = ticketsSold;
        this.ticketPrice = ticketPrice;
        this.organizerName = organizerName;
    }

    public double getRevenue() {
        return ticketsSold * ticketPrice;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public EventType getType() { return type; }
    public LocalDate getDate() { return date; }
    public int getTicketsSold() { return ticketsSold; }
    public double getTicketPrice() { return ticketPrice; }
    public String getOrganizerName() { return organizerName; }
}
