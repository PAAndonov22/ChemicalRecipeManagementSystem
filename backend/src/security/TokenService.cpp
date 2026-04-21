#include "TokenService.h"
#include "../../third_party/picosha2.h"

#include <random>
#include <string>
#include <vector>

std::string TokenService::generateToken() const {
    std::random_device randomDevice;
    std::uniform_int_distribution<int> distribution(0, 255);
    std::vector<unsigned char> bytes(32);
    for (auto& value : bytes) {
        value = static_cast<unsigned char>(distribution(randomDevice));
    }
    return picosha2::bytes_to_hex_string(bytes.begin(), bytes.end());
}

std::string TokenService::hashToken(const std::string& token) const {
    std::vector<unsigned char> digest(picosha2::k_digest_size);
    picosha2::hash256(token.begin(), token.end(), digest.begin(), digest.end());
    return picosha2::bytes_to_hex_string(digest.begin(), digest.end());
}
