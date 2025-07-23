package work.anyway.host;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * 应用程序主入口
 * 创建 Vert.x 实例并部署 MainVerticle
 */
public class MainLauncher {
  private static final Logger LOG = LoggerFactory.getLogger(MainLauncher.class);

  public static void main(String[] args) {
    // Configure logging before starting the application
    configureLogging();

    LOG.info("Starting Direct-LLM-Rask application...");

    // 创建 Vert.x 实例
    Vertx vertx = Vertx.vertx();

    // 部署 MainVerticle
    vertx.deployVerticle(new MainVerticle(), res -> {
      if (res.succeeded()) {
        LOG.info("Application started successfully");
      } else {
        LOG.error("Failed to start application", res.cause());
        System.exit(1);
      }
    });
  }

  /**
   * Configure logging levels from application.properties
   */
  private static void configureLogging() {
    // Get the logger context
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    // Set root logger level
    String rootLevel = ConfigLoader.getString("logging.level", "INFO");
    ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.setLevel(Level.toLevel(rootLevel));

    // Set specific logger levels
    ConfigLoader.getProperties().forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("logging.level.") && !keyStr.equals("logging.level")) {
        String loggerName = keyStr.substring("logging.level.".length());
        String level = value.toString();

        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(loggerName);
        logger.setLevel(Level.toLevel(level));
      }
    });
  }
}