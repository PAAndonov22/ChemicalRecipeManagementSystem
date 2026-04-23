package com.crms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.crms.model.DomainModels.AuditEntry;
import com.crms.repository.AuditRepository;

import jakarta.servlet.http.HttpServletRequest;

import static com.crms.util.ValidationUtils.optionalIntQuery;
import static com.crms.util.ValidationUtils.optionalQuery;

@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public void log(Optional<Integer> userId, String action, String entityType, Optional<Integer> entityId, String details, String ipAddress) {
        repository.createEntry(userId, action, entityType, entityId, details, ipAddress);
    }

    public Map<String, Object> listLogs(HttpServletRequest request) {
        int limit = optionalIntQuery(request, "limit", 50);
        String action = optionalQuery(request, "action");
        String entityType = optionalQuery(request, "entityType");
        String actor = optionalQuery(request, "actor");
        String dateFrom = optionalQuery(request, "dateFrom");
        String dateTo = optionalQuery(request, "dateTo");

        List<Map<String, Object>> items = new ArrayList<>();
        for (AuditEntry entry : repository.listEntries(limit, action, entityType, actor, dateFrom, dateTo)) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", entry.userId().orElse(null));
            user.put("username", entry.username());
            user.put("email", entry.email());
            user.put("roleName", entry.roleName());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.id());
            item.put("action", entry.action());
            item.put("entityType", entry.entityType());
            item.put("entityId", entry.entityId().orElse(null));
            item.put("details", entry.details());
            item.put("ipAddress", entry.ipAddress());
            item.put("createdAt", entry.createdAt());
            item.put("user", user);
            items.add(item);
        }

        return Map.of("items", items);
    }

    public String exportCsv(HttpServletRequest request) {
        int limit = optionalIntQuery(request, "limit", 250);
        String action = optionalQuery(request, "action");
        String entityType = optionalQuery(request, "entityType");
        String actor = optionalQuery(request, "actor");
        String dateFrom = optionalQuery(request, "dateFrom");
        String dateTo = optionalQuery(request, "dateTo");

        StringBuilder builder = new StringBuilder("created_at,action,entity_type,entity_id,username,email,role_name,ip_address,details\n");
        for (AuditEntry entry : repository.listEntries(limit, action, entityType, actor, dateFrom, dateTo)) {
            builder.append(csv(entry.createdAt())).append(',')
                .append(csv(entry.action())).append(',')
                .append(csv(entry.entityType())).append(',')
                .append(csv(entry.entityId().map(String::valueOf).orElse(""))).append(',')
                .append(csv(entry.username())).append(',')
                .append(csv(entry.email())).append(',')
                .append(csv(entry.roleName())).append(',')
                .append(csv(entry.ipAddress())).append(',')
                .append(csv(entry.details())).append('\n');
        }
        return builder.toString();
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
