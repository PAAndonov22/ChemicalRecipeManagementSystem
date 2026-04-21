#include "AuditService.h"
#include "../repositories/AuditRepository.h"

AuditService::AuditService(AuditRepository& repository) : repository_(repository) {}

void AuditService::log(const std::optional<int>& userId, const std::string& action, const std::string& entityType, const std::optional<int>& entityId, const std::string& details, const std::string& ipAddress) const {
    repository_.createEntry(userId, action, entityType, entityId, details, ipAddress);
}

json AuditService::listLogs(const httplib::Request& request) const {
    const int limit = optionalIntQuery(request, "limit", 50);
    const std::string action = optionalQuery(request, "action");

    json logs = json::array();
    for (const auto& entry : repository_.listEntries(limit, action)) {
        logs.push_back({
            {"id", entry.id},
            {"action", entry.action},
            {"entityType", entry.entityType},
            {"entityId", entry.entityId.has_value() ? json(entry.entityId.value()) : json(nullptr)},
            {"details", entry.details},
            {"ipAddress", entry.ipAddress},
            {"createdAt", entry.createdAt},
            {"user", {
                {"id", entry.userId.has_value() ? json(entry.userId.value()) : json(nullptr)},
                {"username", entry.username},
                {"email", entry.email},
                {"roleName", entry.roleName}
            }}
        });
    }

    return {{"items", logs}};
}
