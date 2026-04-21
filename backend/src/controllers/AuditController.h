#pragma once

namespace httplib {
class Server;
}

class AuthService;
class AuditService;

class AuditController {
public:
    AuditController(AuthService& authService, AuditService& auditService);

    void registerRoutes(httplib::Server& server) const;

private:
    AuthService& authService_;
    AuditService& auditService_;
};
