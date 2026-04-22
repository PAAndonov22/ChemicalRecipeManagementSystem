#include "AuditService.h"
#include "../repositories/AuditRepository.h"

#include <sstream>

AuditService::AuditService(AuditRepository& repository) : repository_(repository) {}

void AuditService::log(const std::optional<int>& userId, const std::string& action, const std::string& entityType, const std::optional<int>& entityId, const std::string& details, const std::string& ipAddress) const {
    repository_.createEntry(userId, action, entityType, entityId, details, ipAddress);
}

json AuditService::listLogs(const httplib::Request& request) const {
    const int limit = optionalIntQuery(request, "limit", 50);
    const std::string action = optionalQuery(request, "action");
    const std::string entityType = optionalQuery(request, "entityType");
    const std::string actor = optionalQuery(request, "actor");
    const std::string dateFrom = optionalQuery(request, "dateFrom");
    const std::string dateTo = optionalQuery(request, "dateTo");

    json logs = json::array();
    for (const auto& entry : repository_.listEntries(limit, action, entityType, actor, dateFrom, dateTo)) {
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

std::string AuditService::exportCsv(const httplib::Request& request) const {
    const int limit = optionalIntQuery(request, "limit", 250);
    const std::string action = optionalQuery(request, "action");
    const std::string entityType = optionalQuery(request, "entityType");
    const std::string actor = optionalQuery(request, "actor");
    const std::string dateFrom = optionalQuery(request, "dateFrom");
    const std::string dateTo = optionalQuery(request, "dateTo");

    auto escapeCsv = [](const std::string& value) {
        std::string escaped = value;
        std::size_t offset = 0;
        while ((offset = escaped.find('"', offset)) != std::string::npos) {
            escaped.insert(offset, 1, '"');
            offset += 2;
        }
        return "\"" + escaped + "\"";
    };

    std::ostringstream stream;
    stream << "created_at,action,entity_type,entity_id,username,email,role_name,ip_address,details\n";
    for (const auto& entry : repository_.listEntries(limit, action, entityType, actor, dateFrom, dateTo)) {
        stream
            << escapeCsv(entry.createdAt) << ','
            << escapeCsv(entry.action) << ','
            << escapeCsv(entry.entityType) << ','
            << escapeCsv(entry.entityId.has_value() ? std::to_string(entry.entityId.value()) : "") << ','
            << escapeCsv(entry.username) << ','
            << escapeCsv(entry.email) << ','
            << escapeCsv(entry.roleName) << ','
            << escapeCsv(entry.ipAddress) << ','
            << escapeCsv(entry.details) << '\n';
    }

    return stream.str();
}
