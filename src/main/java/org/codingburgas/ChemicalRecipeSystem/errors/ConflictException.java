package org.codingburgas.ChemicalRecipeSystem.errors;

/** Two people changed the same record - the second one gets this instead of silently overwriting (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
