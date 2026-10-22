package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;

/** A paid event (workshop, training...). Money collected = tickets sold * ticket price. */
@Entity
public class Event extends BaseEntity {
    private String title;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private LocalDate date;
    private int ticketsSold;
    private double ticketPrice;

    /** Many-to-One association: the employee who organizes the event. */
    @ManyToOne
    private User organizer;

    protected Event() {
    }

    public Event(String title, EventType type, LocalDate date, int ticketsSold, double ticketPrice, User organizer) {
        this.title = title;
        this.type = type;
        this.date = date;
        this.ticketsSold = ticketsSold;
        this.ticketPrice = ticketPrice;
        this.organizer = organizer;
    }

    public double getRevenue() {
        return ticketsSold * ticketPrice;
    }

    public String getTitle() { return title; }
    public EventType getType() { return type; }
    public LocalDate getDate() { return date; }
    public int getTicketsSold() { return ticketsSold; }
    public double getTicketPrice() { return ticketPrice; }
    public User getOrganizer() { return organizer; }
}
