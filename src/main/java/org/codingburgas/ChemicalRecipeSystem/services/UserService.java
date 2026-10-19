package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper;
import org.codingburgas.ChemicalRecipeSystem.dto.ProfileForm;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.dto.UserForm;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.codingburgas.ChemicalRecipeSystem.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** All user logic. Works with User entities inside, gives only UserDto objects out. */
@Service
public class UserService {
    private static final Set<String> SORTABLE = Set.of("fullName", "email", "role", "createdAt");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<UserDto> login(String email, String password) {
        return users.findByEmail(email.trim().toLowerCase())
            .filter(User::isActive)
            .filter(user -> passwordEncoder.matches(password, user.getPassword()))
            .map(DtoMapper::toUserDto);
    }

    public UserDto get(Long id) {
        return DtoMapper.toUserDto(find(id));
    }

    /** Admin search: text + role + status filter, sorted and split into pages. */
    public Page<UserDto> search(String text, Role role, Boolean active, String sort, String direction, int page, int size) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size, Sort.by(dir, sortField));
        return users.search(text.trim(), role, active, pageRequest).map(DtoMapper::toUserDto);
    }

    public UserDto create(UserForm form) {
        return create(form.getFullName(), form.getEmail(), form.getPhone(), form.getPassword(), form.getRole());
    }

    public UserDto create(String fullName, String email, String phone, String password, Role role) {
        if (fullName == null || fullName.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Name and email are required.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Choose a role.");
        }
        email = email.trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email " + email + " is already used.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPhone(phone == null ? "" : phone.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return DtoMapper.toUserDto(users.save(user));
    }

    /** Update name, email and phone. */
    public UserDto updateProfile(Long id, ProfileForm form) {
        User user = find(id);
        String email = form.getEmail().trim().toLowerCase();
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email " + email + " is already used.");
        }
        user.setFullName(form.getFullName().trim());
        user.setEmail(email);
        user.setPhone(form.getPhone() == null ? "" : form.getPhone().trim());
        return DtoMapper.toUserDto(users.save(user));
    }

    /** Only called from admin pages. An admin cannot demote or deactivate themselves. */
    public void changeRole(Long id, Role role, Long currentUserId) {
        if (id.equals(currentUserId) && role != Role.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own ADMIN role.");
        }
        User user = find(id);
        user.setRole(role);
        users.save(user);
    }

    /** Soft delete: the user stays in the database but can no longer log in. */
    public void setActive(Long id, boolean active, Long currentUserId) {
        if (id.equals(currentUserId) && !active) {
            throw new IllegalArgumentException("You cannot deactivate yourself.");
        }
        User user = find(id);
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

    public List<UserDto> registeredSince(String period) {
        return users.findByCreatedAtAfterOrderByCreatedAtDesc(periodStart(period)).stream()
            .map(DtoMapper::toUserDto)
            .toList();
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

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
}
