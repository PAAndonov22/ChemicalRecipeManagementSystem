package org.codingburgas.ChemicalRecipeSystem.errors;

/** The user is logged in but may not do this - shown as a 403 page / HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
