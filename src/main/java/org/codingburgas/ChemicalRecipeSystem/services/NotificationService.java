package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDateTime;

import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.Notification;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.NotificationRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications. A user only ever reads and changes their own. */
@Service
@Transactional
public class NotificationService {
    public record NotificationDto(Long id, String message, String link, boolean seen, LocalDateTime createdAt) {
    }

    private final NotificationRepository repo;
    private final UserRepository users;
    private final PreferencesService preferences;

    public NotificationService(NotificationRepository repo, UserRepository users, PreferencesService preferences) {
        this.repo = repo;
        this.users = users;
        this.preferences = preferences;
    }

    /** kind: STATUS or COMMENT (the user can switch these off) or SYSTEM (always). Returns false if skipped. */
    public boolean notifyUser(Long userId, String kind, String message, String link, String dedupKey) {
        if (userId == null || (dedupKey != null && repo.existsByUserIdAndDedupKey(userId, dedupKey))) {
            return false;
        }
        var prefs = preferences.get(userId);
        if (("STATUS".equals(kind) && !prefs.notifyStatus()) || ("COMMENT".equals(kind) && !prefs.notifyComments())) {
            return false;
        }
        repo.save(new Notification(userId, message, link, dedupKey));
        return true;
    }

    public void notifyRole(Role role, String kind, String message, String link, Long exceptUserId) {
        for (User user : users.findByRoleAndActiveTrue(role)) {
            if (!user.getId().equals(exceptUserId)) {
                notifyUser(user.getId(), kind, message, link, null);
            }
        }
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return repo.countByUserIdAndSeenFalse(userId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> page(Long userId, int page, int size) {
        return repo.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(Math.max(page, 0), size))
            .map(n -> new NotificationDto(n.getId(), n.getMessage(), n.getLink(), n.isSeen(), n.getCreatedAt()));
    }

    public void markRead(Long id, Long userId) {
        repo.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Notification not found.")).setSeen(true);
    }

    public void markAllRead(Long userId) {
        repo.markAllSeen(userId);
    }
}
