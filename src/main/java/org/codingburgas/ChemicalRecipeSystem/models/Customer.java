package org.codingburgas.ChemicalRecipeSystem.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

/** A client company that orders recipes. fullName / email / phone (from Person) are the contact person's. */
@Entity
public class Customer extends Person {
    @Column(nullable = false, unique = true)
    private String companyName;

    private String city;

    /**
     * One-to-Many, aggregation: a customer has many orders, but the orders are not
     * created or deleted through the customer (no cascade) - they have their own life.
     * Inverse side - the foreign key customer_id is in the orders table (see Order.customer).
     */
    @OneToMany(mappedBy = "customer")
    private List<Order> orders = new ArrayList<>();

    protected Customer() {
    }

    public Customer(String companyName, String contactName, String email, String phone, String city) {
        this.companyName = companyName;
        this.city = city;
        setFullName(contactName);
        setEmail(email);
        setPhone(phone);
    }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public List<Order> getOrders() { return Collections.unmodifiableList(orders); }
}
