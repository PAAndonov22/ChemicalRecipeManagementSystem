package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Set<String> SORTABLE = Set.of("fullName", "email", "role", "createdAt");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> login(String email, String password) {
        return users.findByEmail(email.trim().toLowerCase())
            .filter(User::isActive)
            .filter(user -> passwordEncoder.matches(password, user.getPassword()));
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    /** Admin search: text + role + status filter, sorted and split into pages. */
    public Page<User> search(String text, Role role, Boolean active, String sort, String direction, int page, int size) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size, Sort.by(dir, sortField));
        return users.search(text.trim(), role, active, pageRequest);
    }

    public User create(String fullName, String email, String phone, String password, Role role) {
        email = email.trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email " + email + " is already used.");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPhone(phone.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return users.save(user);
    }

    /** Update name, email and phone. */
    public User updateProfile(Long id, String fullName, String email, String phone) {
        User user = get(id);
        email = email.trim().toLowerCase();
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email " + email + " is already used.");
        }
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPhone(phone.trim());
        return users.save(user);
    }

    /** Only called from admin pages. An admin cannot demote or deactivate themselves. */
    public void changeRole(Long id, Role role, User admin) {
        if (id.equals(admin.getId()) && role != Role.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own ADMIN role.");
        }
        User user = get(id);
        user.setRole(role);
        users.save(user);
    }

    /** Soft delete: the user stays in the database but can no longer log in. */
    public void setActive(Long id, boolean active, User admin) {
        if (id.equals(admin.getId()) && !active) {
            throw new IllegalArgumentException("You cannot deactivate yourself.");
        }
        User user = get(id);
        user.setActive(active);
        users.save(user);
    }

    /** Start time of a period: today, week, month or year back. */
    public LocalDateTime periodStart(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "week" -> now.minusWeeks(1);
            case "month" -> now.minusMonths(1);
            case "year" -> now.minusYears(1);
            default -> LocalDate.now().atStartOfDay(); // today
        };
    }

    public Map<String, Long> registrationCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String period : List.of("today", "week", "month", "year")) {
            counts.put(period, users.countByCreatedAtAfter(periodStart(period)));
        }
        return counts;
    }

    public List<User> registeredSince(String period) {
        return users.findByCreatedAtAfterOrderByCreatedAtDesc(periodStart(period));
    }

    /** New users per month for the last 12 months (oldest first) - for the bar chart. */
    public Map<YearMonth, Long> registrationsPerMonth() {
        Map<YearMonth, Long> result = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            result.put(current.minusMonths(i), 0L);
        }
        for (User user : users.findByCreatedAtAfterOrderByCreatedAtDesc(current.minusMonths(11).atDay(1).atStartOfDay())) {
            result.merge(YearMonth.from(user.getCreatedAt()), 1L, Long::sum);
        }
        return result;
    }
}
