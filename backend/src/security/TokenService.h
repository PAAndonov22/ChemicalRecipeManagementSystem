#pragma once

#include <string>

class TokenService {
public:
    std::string generateToken() const;
    std::string hashToken(const std::string& token) const;
};
