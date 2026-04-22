#pragma once

#include "../models/DomainModels.h"

#include <optional>
#include <string>
#include <vector>

class Database;

class AuditRepository {
public:
    explicit AuditRepository(Database& database);

    void createEntry(const std::optional<int>& userId, const std::string& action, const std::string& entityType, const std::optional<int>& entityId, const std::string& details, const std::string& ipAddress) const;
    std::vector<AuditEntry> listEntries(int limit, const std::string& actionFilter, const std::string& entityTypeFilter, const std::string& actorFilter, const std::string& dateFrom, const std::string& dateTo) const;

private:
    Database& database_;
};
