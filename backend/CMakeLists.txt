set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CRMS_SOURCES
    src/main.cpp
    src/database/Database.cpp
    src/database/Migrations.cpp
    src/security/PasswordHasher.cpp
    src/security/TokenService.cpp
    src/repositories/AuthRepository.cpp
    src/repositories/AuditRepository.cpp
    src/repositories/RecipeRepository.cpp
    src/repositories/ReportRepository.cpp
    src/services/AuditService.cpp
    src/services/AuthService.cpp
    src/services/RecipeService.cpp
    src/services/ReportService.cpp
    src/controllers/AuthController.cpp
    src/controllers/RecipeController.cpp
    src/controllers/AuditController.cpp
    src/controllers/ReportController.cpp
    third_party/sqlite/sqlite3.c
)

add_executable(crms_server ${CRMS_SOURCES})

target_include_directories(crms_server
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/sqlite
)

target_compile_definitions(crms_server
    PRIVATE
        CRMS_PROJECT_ROOT="${CMAKE_SOURCE_DIR}"
        _CRT_SECURE_NO_WARNINGS
        SQLITE_THREADSAFE=1
        SQLITE_ENABLE_FTS5
        SQLITE_ENABLE_JSON1
)

if(MSVC)
    target_compile_options(crms_server PRIVATE /W4 /EHsc)
else()
    target_compile_options(crms_server PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(WIN32)
    target_link_libraries(crms_server PRIVATE ws2_32)
endif()
