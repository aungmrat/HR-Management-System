package hrms.config;

import hrms.repository.UserRepository;
import hrms.security.LoginAttemptGuard;
import hrms.service.AuditService;
import hrms.service.AuthService;

/**
 * Minimal manual dependency-injection container. Slim on purpose: only the
 * services the application actually has today. Add a field, a line in
 * {@link #initialize()} and a getter when the next service lands.
 *
 * <p>Initialized once during bootstrap; UI code resolves collaborators from
 * here instead of constructing their own graphs.</p>
 */
public final class ServiceRegistry {

    private static volatile AuditService auditService;
    private static volatile AuthService authService;

    private ServiceRegistry() {
    }

    /** Wires the service singletons. Idempotent. */
    public static synchronized void initialize() {
        if (authService != null) {
            return;
        }
        auditService = new AuditService();
        authService = new AuthService(new UserRepository(), auditService, new LoginAttemptGuard());
    }

    public static AuditService auditService() {
        require(auditService, "AuditService");
        return auditService;
    }

    public static AuthService authService() {
        require(authService, "AuthService");
        return authService;
    }

    private static void require(Object instance, String name) {
        if (instance == null) {
            throw new IllegalStateException(
                    name + " is not initialized; call ServiceRegistry.initialize() first");
        }
    }
}
