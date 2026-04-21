#pragma once

#include <stdexcept>
#include <string>

class HttpException : public std::runtime_error {
public:
    HttpException(int status, const std::string& message)
        : std::runtime_error(message), status_(status) {}

    int status() const noexcept {
        return status_;
    }

private:
    int status_;
};
