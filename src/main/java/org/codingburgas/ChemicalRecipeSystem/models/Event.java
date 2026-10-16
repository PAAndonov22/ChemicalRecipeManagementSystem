package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A paid event (workshop, training...). Money collected = tickets sold * ticket price. */
@Entity
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private LocalDate date;
    private int ticketsSold;
    private double ticketPrice;

    public Event() {
    }

    public Event(String title, EventType type, LocalDate date, int ticketsSold, double ticketPrice) {
        this.title = title;
        this.type = type;
        this.date = date;
        this.ticketsSold = ticketsSold;
        this.ticketPrice = ticketPrice;
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
}
