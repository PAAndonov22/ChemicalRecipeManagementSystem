#pragma once

class Database;
class PasswordHasher;

namespace migrations {
void apply(Database& database, const PasswordHasher& passwordHasher);
}
