package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDateTime;

import org.codingburgas.ChemicalRecipeSystem.models.JobRun;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.JobRunRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Scheduled job: tells the admins about recipes that wait too long for a review. Safe to run twice (dedup key). */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final RecipeRepository recipes;
    private final UserRepository users;
    private final NotificationService notifications;
    private final JobRunRepository jobRuns;
    private final int reviewDays;

    public ReminderService(RecipeRepository recipes, UserRepository users, NotificationService notifications,
                           JobRunRepository jobRuns, @Value("${crms.reminders.review-days:3}") int reviewDays) {
        this.recipes = recipes;
        this.users = users;
        this.notifications = notifications;
        this.jobRuns = jobRuns;
        this.reviewDays = reviewDays;
    }

    @Scheduled(cron = "${crms.reminders.cron:0 0 * * * *}", zone = "${crms.reminders.zone:Europe/Sofia}")
    public void scheduled() {
        run(LocalDateTime.now());
    }

    public int run(LocalDateTime now) {
        int created = 0;
        try {
            for (Recipe r : recipes.findByStatusAndDeletedAtIsNull(RecipeStatus.IN_REVIEW)) {
                if (r.getStatusChangedAt().isBefore(now.minusDays(reviewDays))) {
                    for (User admin : users.findByRoleAndActiveTrue(Role.ADMIN)) {
                        String key = "stale-review:" + r.getId() + ":" + r.getStatusChangedAt();
                        if (notifications.notifyUser(admin.getId(), "SYSTEM",
                            "Recipe " + r.getName() + " has waited more than " + reviewDays + " days for review.", "/recipes/" + r.getId(), key)) {
                            created++;
                        }
                    }
                }
            }
            jobRuns.save(new JobRun("stale-review-reminders", created, "ok"));
        } catch (RuntimeException e) {
            log.error("Reminder job failed", e);
            jobRuns.save(new JobRun("stale-review-reminders", created, "failed: " + e.getMessage()));
        }
        log.info("Reminder job finished, {} new notification(s)", created);
        return created;
    }
}
