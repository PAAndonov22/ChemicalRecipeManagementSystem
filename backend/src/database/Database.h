#pragma once

#include "../../third_party/sqlite/sqlite3.h"

#include <string>

class Database {
public:
    explicit Database(const std::string& path);
    ~Database();

    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;

    sqlite3* connection() const noexcept;
    void execute(const std::string& sql) const;
    void beginTransaction() const;
    void commit() const;
    void rollback() const;
    long long lastInsertId() const;

private:
    sqlite3* connection_{};
};
