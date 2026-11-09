package org.codingburgas.ChemicalRecipeSystem.errors;

/** The requested record does not exist (or is in the trash) - shown as a 404 page / HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
