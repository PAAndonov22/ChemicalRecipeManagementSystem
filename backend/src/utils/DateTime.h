#pragma once

#include <ctime>
#include <iomanip>
#include <sstream>
#include <string>

inline std::string formatUtc(std::time_t value) {
    std::tm utc{};
#ifdef _WIN32
    gmtime_s(&utc, &value);
#else
    gmtime_r(&value, &utc);
#endif

    std::ostringstream stream;
    stream << std::put_time(&utc, "%Y-%m-%d %H:%M:%S");
    return stream.str();
}

inline std::string currentUtcTimestamp() {
    return formatUtc(std::time(nullptr));
}

inline std::string futureUtcTimestampHours(int hours) {
    return formatUtc(std::time(nullptr) + static_cast<std::time_t>(hours) * 60 * 60);
}

inline std::string futureUtcTimestampMinutes(int minutes) {
    return formatUtc(std::time(nullptr) + static_cast<std::time_t>(minutes) * 60);
}
