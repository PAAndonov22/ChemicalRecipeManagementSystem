#include "PasswordHasher.h"
#include "../../third_party/picosha2.h"

#include <algorithm>
#include <cstdint>
#include <random>
#include <string>
#include <vector>

namespace {
constexpr std::size_t kSha256BlockSize = 64;
constexpr int kPbkdf2Iterations = 120000;

std::vector<unsigned char> toBytes(const std::string& value) {
    return std::vector<unsigned char>(value.begin(), value.end());
}

std::vector<unsigned char> sha256(const std::vector<unsigned char>& data) {
    std::vector<unsigned char> digest(picosha2::k_digest_size);
    picosha2::hash256(data.begin(), data.end(), digest.begin(), digest.end());
    return digest;
}

std::vector<unsigned char> hmacSha256(const std::vector<unsigned char>& key, const std::vector<unsigned char>& message) {
    std::vector<unsigned char> preparedKey = key;
    if (preparedKey.size() > kSha256BlockSize) {
        preparedKey = sha256(preparedKey);
    }
    preparedKey.resize(kSha256BlockSize, 0x00);

    std::vector<unsigned char> innerPad(kSha256BlockSize);
    std::vector<unsigned char> outerPad(kSha256BlockSize);
    for (std::size_t index = 0; index < kSha256BlockSize; ++index) {
        innerPad[index] = static_cast<unsigned char>(preparedKey[index] ^ 0x36);
        outerPad[index] = static_cast<unsigned char>(preparedKey[index] ^ 0x5c);
    }

    std::vector<unsigned char> inner(innerPad);
    inner.insert(inner.end(), message.begin(), message.end());
    const auto innerHash = sha256(inner);

    std::vector<unsigned char> outer(outerPad);
    outer.insert(outer.end(), innerHash.begin(), innerHash.end());
    return sha256(outer);
}

std::vector<unsigned char> pbkdf2Sha256(const std::string& password, const std::string& salt, int iterations, std::size_t outputSize) {
    std::vector<unsigned char> passwordBytes = toBytes(password);
    std::vector<unsigned char> saltBytes = toBytes(salt);
    std::vector<unsigned char> derived(outputSize);

    const std::size_t hashLength = picosha2::k_digest_size;
    const std::size_t blockCount = (outputSize + hashLength - 1) / hashLength;

    for (std::size_t blockIndex = 1; blockIndex <= blockCount; ++blockIndex) {
        std::vector<unsigned char> initialMessage(saltBytes);
        initialMessage.push_back(static_cast<unsigned char>((blockIndex >> 24) & 0xff));
        initialMessage.push_back(static_cast<unsigned char>((blockIndex >> 16) & 0xff));
        initialMessage.push_back(static_cast<unsigned char>((blockIndex >> 8) & 0xff));
        initialMessage.push_back(static_cast<unsigned char>(blockIndex & 0xff));

        auto result = hmacSha256(passwordBytes, initialMessage);
        auto accumulator = result;

        for (int iteration = 1; iteration < iterations; ++iteration) {
            result = hmacSha256(passwordBytes, result);
            for (std::size_t index = 0; index < accumulator.size(); ++index) {
                accumulator[index] ^= result[index];
            }
        }

        const std::size_t offset = (blockIndex - 1) * hashLength;
        const std::size_t remaining = std::min(hashLength, outputSize - offset);
        std::copy_n(accumulator.begin(), remaining, derived.begin() + static_cast<std::ptrdiff_t>(offset));
    }

    return derived;
}
}

std::string PasswordHasher::generateSalt() const {
    std::random_device randomDevice;
    std::uniform_int_distribution<int> distribution(0, 255);
    std::vector<unsigned char> bytes(16);
    for (auto& value : bytes) {
        value = static_cast<unsigned char>(distribution(randomDevice));
    }
    return picosha2::bytes_to_hex_string(bytes.begin(), bytes.end());
}

std::string PasswordHasher::hashPassword(const std::string& password, const std::string& salt) const {
    const auto digest = pbkdf2Sha256(password, salt, kPbkdf2Iterations, 32);
    return picosha2::bytes_to_hex_string(digest.begin(), digest.end());
}

bool PasswordHasher::verifyPassword(const std::string& password, const std::string& salt, const std::string& expectedHash) const {
    return hashPassword(password, salt) == expectedHash;
}
