#pragma once

#include "../utils/HttpException.h"
#include "../../third_party/sqlite/sqlite3.h"

#include <string>

inline void ensureSqliteResult(int result, sqlite3* db, const std::string& context) {
    if (result != SQLITE_OK && result != SQLITE_DONE && result != SQLITE_ROW) {
        throw HttpException(500, context + ": " + sqlite3_errmsg(db));
    }
}

inline std::string columnText(sqlite3_stmt* statement, int index) {
    const auto* raw = reinterpret_cast<const char*>(sqlite3_column_text(statement, index));
    return raw == nullptr ? "" : std::string(raw);
}

class Statement final {
public:
    Statement(sqlite3* db, const std::string& sql) : db_(db), statement_(nullptr) {
        ensureSqliteResult(sqlite3_prepare_v2(db_, sql.c_str(), -1, &statement_, nullptr), db_, "Failed to prepare SQLite statement");
    }

    ~Statement() {
        if (statement_ != nullptr) {
            sqlite3_finalize(statement_);
        }
    }

    Statement(const Statement&) = delete;
    Statement& operator=(const Statement&) = delete;

    sqlite3_stmt* get() const noexcept {
        return statement_;
    }

private:
    sqlite3* db_;
    sqlite3_stmt* statement_;
};
