#pragma once

#include "../models/DomainModels.h"
#include "../utils/JsonUtils.h"

#include <optional>
#include <string>

class AuditRepository;

class AuditService {
public:
    explicit AuditService(AuditRepository& repository);

    void log(const std::optional<int>& userId, const std::string& action, const std::string& entityType, const std::optional<int>& entityId, const std::string& details, const std::string& ipAddress) const;
    json listLogs(const httplib::Request& request) const;
    std::string exportCsv(const httplib::Request& request) const;

private:
    AuditRepository& repository_;
};
