package org.codingburgas.ChemicalRecipeSystem.services;

import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.dto.PreferencesDto;
import org.codingburgas.ChemicalRecipeSystem.models.Theme;
import org.codingburgas.ChemicalRecipeSystem.models.UserPreferences;
import org.codingburgas.ChemicalRecipeSystem.repositories.PreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-user settings: theme, rows per page, which notifications to receive. Saved in the database. */
@Service
public class PreferencesService {
    public static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50);
    private final PreferencesRepository repo;

    public PreferencesService(PreferencesRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public PreferencesDto get(Long userId) {
        UserPreferences p = repo.findByUserId(userId).orElseGet(() -> repo.save(new UserPreferences(userId)));
        return toDto(p);
    }

    @Transactional
    public PreferencesDto save(Long userId, Theme theme, int pageSize, boolean notifyStatus, boolean notifyComments) {
        if (!PAGE_SIZES.contains(pageSize)) {
            throw new IllegalArgumentException("Rows per page must be 10, 20 or 50.");
        }
        UserPreferences p = repo.findByUserId(userId).orElseGet(() -> new UserPreferences(userId));
        p.setTheme(theme);
        p.setPageSize(pageSize);
        p.setNotifyStatus(notifyStatus);
        p.setNotifyComments(notifyComments);
        return toDto(repo.save(p));
    }

    private static PreferencesDto toDto(UserPreferences p) {
        return new PreferencesDto(p.getTheme(), p.getPageSize(), p.isNotifyStatus(), p.isNotifyComments());
    }
}
