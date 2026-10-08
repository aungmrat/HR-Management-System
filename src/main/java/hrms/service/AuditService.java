package hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security audit trail. <b>Placeholder implementation:</b> entries go to the
 * application log only. The {@code audit_logs} table (user_id, action, module,
 * entity, entity_id, description, ip_address, device_info) will replace the
 * log write once the schema lands; the {@link #record} signature already
 * matches it, so callers will not change.
 */
public class AuditService {

    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    public void record(String action, String module, String entity, Long entityId,
                       String description) {
        LOG.info("AUDIT | {} | {} | {} | {} | {}", action, module, entity, entityId, description);
    }
}
