package hrms.config;

import hrms.db.DbChecker;
import hrms.db.DbChecker.PingResult;
import hrms.exception.HrmsException;
import hrms.repository.Sql;
import hrms.ui.theme.ThemeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hrms.ui.login.LoginFrame;

/**
 * Orchestrates application startup:
 * <ol>
 * <li>load configuration</li>
 * <li>probe MySQL connectivity with friendly diagnostics</li>
 * <li>initialize the connection pool</li>
 * <li>apply Flyway migrations (creates the database on first run)</li>
 * <li>wire services, install the theme, open the login window</li>
 * </ol>
 */
public final class Bootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(Bootstrapper.class);

  public void launch(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConfig::close, "hrms-shutdown"));
    LOG.info("============================================================");
    try {
      AppConfig config = AppConfig.get();
      LOG.info("{} v{} - starting", config.appName(), config.appVersion());

      PingResult ping = DbChecker.check(config);
      LOG.info("Connected to MySQL {} at {}:{} (database '{}')",
              ping.serverVersion(), ping.host(), ping.port(), ping.database());

      DatabaseConfig.initialize(config);

      long tableCount = new Sql().count(
              "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()");

      LOG.info("------------------------------------------------------------");
      LOG.info("  Database            : {}:{}/{}", ping.host(), ping.port(), ping.database());
      LOG.info("  Tables              : {}", tableCount);
      LOG.info("  Status              : READY");
      LOG.info("============================================================");
      ServiceRegistry.initialize();
      ThemeManager.install();

      javax.swing.SwingUtilities.invokeLater(() -> {
        try {
          LoginFrame frame = new LoginFrame();
          frame.setVisible(true);
        } catch (Exception e) {
          LOG.error("Cannot open the login window", e);
          System.exit(3);
        }
      });
    } catch (HrmsException e) {
      LOG.error("Startup failed: {}", e.getMessage());
      LOG.debug("Startup failure detail", e);
      System.err.println("[STARTUP FAILED] " + e.getUserMessage());
      System.exit(1);
    } catch (Exception e) {
      LOG.error("Unexpected failure during startup", e);
      System.err.println("[STARTUP FAILED] Unexpected error: " + e.getMessage());
      System.exit(2);
    }
  }
}
