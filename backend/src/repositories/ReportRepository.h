#pragma once

#include "../utils/JsonUtils.h"

class Database;

class ReportRepository {
public:
    explicit ReportRepository(Database& database);

    json buildSummary() const;

private:
    Database& database_;
};
