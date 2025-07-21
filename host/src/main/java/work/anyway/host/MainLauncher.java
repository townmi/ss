package work.anyway.host;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用程序主入口
 * 创建 Vert.x 实例并部署 MainVerticle
 */
public class MainLauncher {
  private static final Logger LOG = LoggerFactory.getLogger(MainLauncher.class);

  public static void main(String[] args) {
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
}