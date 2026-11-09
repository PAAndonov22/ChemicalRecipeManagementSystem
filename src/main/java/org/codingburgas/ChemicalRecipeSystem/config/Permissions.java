package org.codingburgas.ChemicalRecipeSystem.config;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.models.Role;

/** Role -> permissions matrix. ADMIN = everything, CHEMIST = editor, the other roles are read-only users. */
public final class Permissions {
    private Permissions() {
    }

    public static Set<Permission> of(Role role) {
        return switch (role) {
            case ADMIN -> EnumSet.allOf(Permission.class);
            case CHEMIST -> EnumSet.of(Permission.RECIPE_READ, Permission.RECIPE_WRITE, Permission.RECIPE_DELETE);
            default -> EnumSet.of(Permission.RECIPE_READ);
        };
    }

    public static boolean has(Role role, Permission permission) {
        return of(role).contains(permission);
    }

    public static Set<String> names(Role role) {
        return of(role).stream().map(Enum::name).collect(Collectors.toSet());
    }
}
