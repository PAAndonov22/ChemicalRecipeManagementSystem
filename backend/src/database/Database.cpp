#include "Database.h"
#include "SqliteUtils.h"
#include "../utils/HttpException.h"

#include <filesystem>

Database::Database(const std::string& path) {
    std::filesystem::path filePath(path);
    if (filePath.has_parent_path()) {
        std::filesystem::create_directories(filePath.parent_path());
    }

    if (sqlite3_open(path.c_str(), &connection_) != SQLITE_OK) {
        const std::string message = connection_ == nullptr ? "unknown SQLite error" : sqlite3_errmsg(connection_);
        throw HttpException(500, "Failed to open SQLite database: " + message);
    }

    execute("PRAGMA foreign_keys = ON;");
    execute("PRAGMA journal_mode = WAL;");
    execute("PRAGMA synchronous = NORMAL;");
}

Database::~Database() {
    if (connection_ != nullptr) {
        sqlite3_close(connection_);
        connection_ = nullptr;
    }
}

sqlite3* Database::connection() const noexcept {
    return connection_;
}

void Database::execute(const std::string& sql) const {
    char* error = nullptr;
    const int result = sqlite3_exec(connection_, sql.c_str(), nullptr, nullptr, &error);
    if (result != SQLITE_OK) {
        const std::string message = error == nullptr ? sqlite3_errmsg(connection_) : std::string(error);
        sqlite3_free(error);
        throw HttpException(500, "SQLite execution failed: " + message);
    }
}

void Database::beginTransaction() const {
    execute("BEGIN IMMEDIATE TRANSACTION;");
}

void Database::commit() const {
    execute("COMMIT;");
}

void Database::rollback() const {
    execute("ROLLBACK;");
}

long long Database::lastInsertId() const {
    return sqlite3_last_insert_rowid(connection_);
}
