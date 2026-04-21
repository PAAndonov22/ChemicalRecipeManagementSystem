#pragma once

namespace httplib {
class Server;
}

class AuthService;

class AuthController {
public:
    explicit AuthController(AuthService& authService);

    void registerRoutes(httplib::Server& server) const;

private:
    AuthService& authService_;
};
