#pragma once

#include "../../third_party/json.hpp"
#include "../../third_party/httplib.h"
#include "HttpException.h"

#include <algorithm>
#include <cctype>
#include <regex>
#include <sstream>
#include <string>
#include <vector>

using json = nlohmann::json;

inline json parseJsonBody(const httplib::Request& request) {
    if (request.body.empty()) {
        return json::object();
    }

    try {
        const json parsed = json::parse(request.body);
        if (!parsed.is_object()) {
            throw HttpException(400, "Request body must be a JSON object.");
        }
        return parsed;
    } catch (const nlohmann::json::exception&) {
        throw HttpException(400, "Request body is not valid JSON.");
    }
}

inline std::string trimCopy(const std::string& value) {
    const auto begin = std::find_if_not(value.begin(), value.end(), [](unsigned char ch) {
        return std::isspace(ch) != 0;
    });
    const auto end = std::find_if_not(value.rbegin(), value.rend(), [](unsigned char ch) {
        return std::isspace(ch) != 0;
    }).base();
    if (begin >= end) {
        return "";
    }
    return std::string(begin, end);
}

inline std::string toLowerCopy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

inline std::string toUpperCopy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::toupper(ch));
    });
    return value;
}

inline std::string normalizeEmail(const std::string& value) {
    static const std::regex emailPattern(R"(^[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}$)", std::regex::icase);
    const std::string normalized = toLowerCopy(trimCopy(value));
    if (!std::regex_match(normalized, emailPattern)) {
        throw HttpException(400, "Email address is invalid.");
    }
    return normalized;
}

inline std::string requireString(const json& payload, const std::string& field, std::size_t minLength = 1, std::size_t maxLength = 4000) {
    if (!payload.contains(field) || !payload.at(field).is_string()) {
        throw HttpException(400, "Missing or invalid field: " + field + ".");
    }

    const std::string value = trimCopy(payload.at(field).get<std::string>());
    if (value.size() < minLength || value.size() > maxLength) {
        std::ostringstream message;
        message << "Field '" << field << "' must be between " << minLength << " and " << maxLength << " characters.";
        throw HttpException(400, message.str());
    }

    return value;
}

inline std::string optionalString(const json& payload, const std::string& field, std::size_t maxLength = 4000) {
    if (!payload.contains(field) || payload.at(field).is_null()) {
        return "";
    }

    if (!payload.at(field).is_string()) {
        throw HttpException(400, "Field '" + field + "' must be a string.");
    }

    const std::string value = trimCopy(payload.at(field).get<std::string>());
    if (value.size() > maxLength) {
        throw HttpException(400, "Field '" + field + "' is too long.");
    }
    return value;
}

inline int optionalIntQuery(const httplib::Request& request, const std::string& key, int fallback) {
    if (!request.has_param(key)) {
        return fallback;
    }

    try {
        return std::stoi(request.get_param_value(key));
    } catch (const std::exception&) {
        throw HttpException(400, "Query parameter '" + key + "' must be an integer.");
    }
}

inline std::string optionalQuery(const httplib::Request& request, const std::string& key) {
    if (!request.has_param(key)) {
        return "";
    }
    return trimCopy(request.get_param_value(key));
}

inline double requirePositiveNumber(const json& value, const std::string& field) {
    if (!value.contains(field) || !value.at(field).is_number()) {
        throw HttpException(400, "Field '" + field + "' must be numeric.");
    }

    const double parsed = value.at(field).get<double>();
    if (parsed <= 0.0) {
        throw HttpException(400, "Field '" + field + "' must be greater than zero.");
    }
    return parsed;
}

inline int requirePositiveInt(const json& value, const std::string& field) {
    if (!value.contains(field) || !value.at(field).is_number_integer()) {
        throw HttpException(400, "Field '" + field + "' must be an integer.");
    }

    const int parsed = value.at(field).get<int>();
    if (parsed <= 0) {
        throw HttpException(400, "Field '" + field + "' must be greater than zero.");
    }
    return parsed;
}

inline std::string requireEnum(const std::string& value, const std::vector<std::string>& allowed, const std::string& field) {
    for (const auto& candidate : allowed) {
        if (candidate == value) {
            return value;
        }
    }
    throw HttpException(400, "Field '" + field + "' has an unsupported value.");
}

inline void sendJson(httplib::Response& response, const json& payload, int status = 200) {
    response.status = status;
    response.set_content(payload.dump(2), "application/json");
}

template <typename Callback>
void handleJson(const httplib::Request& request, httplib::Response& response, Callback&& callback, int successStatus = 200) {
    try {
        sendJson(response, callback(request), successStatus);
    } catch (const HttpException& exception) {
        sendJson(response, json{
            {"error", exception.what()},
            {"status", exception.status()}
        }, exception.status());
    } catch (const std::exception& exception) {
        sendJson(response, json{
            {"error", "Unexpected server error."},
            {"details", exception.what()}
        }, 500);
    }
}
