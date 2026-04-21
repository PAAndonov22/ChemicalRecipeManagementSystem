#pragma once

#include <string>

class PasswordHasher {
public:
    std::string generateSalt() const;
    std::string hashPassword(const std::string& password, const std::string& salt) const;
    bool verifyPassword(const std::string& password, const std::string& salt, const std::string& expectedHash) const;
};
