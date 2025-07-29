package work.anyway.host;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 开发模式文件监控器
 * 监控class文件变化，支持热重载功能
 */
public class DevModeFileWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(DevModeFileWatcher.class);

  private final Vertx vertx;
  private final ApplicationContext applicationContext;
  private WatchService watchService;
  private final AtomicBoolean watching = new AtomicBoolean(false);
  private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
  private final Map<String, Long> lastModified = new ConcurrentHashMap<>();

  // 重载延迟时间（毫秒），避免频繁重载
  private static final long RELOAD_DELAY = 1000;

  public DevModeFileWatcher(Vertx vertx, ApplicationContext applicationContext) {
    this.vertx = vertx;
    this.applicationContext = applicationContext;
  }

  /**
   * 开始监控指定目录
   */
  public void startWatching(String... directories) {
    if (watching.get()) {
      LOG.warn("File watcher is already running");
      return;
    }

    try {
      watchService = FileSystems.getDefault().newWatchService();

      for (String dir : directories) {
        registerDirectory(dir);
      }

      if (!watchKeys.isEmpty()) {
        watching.set(true);
        CompletableFuture.runAsync(this::watchLoop);
        LOG.info("Hot reload file watcher started, monitoring {} directories", watchKeys.size());
      } else {
        LOG.warn("No valid directories found to watch");
      }

    } catch (IOException e) {
      LOG.error("Failed to start file watcher", e);
    }
  }

  /**
   * 注册目录进行监控
   */
  private void registerDirectory(String dirPattern) {
    try {
      if (dirPattern.contains("*")) {
        // 处理通配符模式，如 "services/*/target/classes"
        String basePath = dirPattern.substring(0, dirPattern.indexOf("*"));
        String suffix = dirPattern.substring(dirPattern.indexOf("*") + 1);

        File baseDir = new File(basePath);
        if (baseDir.exists() && baseDir.isDirectory()) {
          File[] subdirs = baseDir.listFiles(File::isDirectory);
          if (subdirs != null) {
            for (File subdir : subdirs) {
              File classesDir = new File(subdir, suffix);
              if (classesDir.exists() && classesDir.isDirectory()) {
                registerSingleDirectory(classesDir.toPath());
              }
            }
          }
        }
      } else {
        // 直接路径
        Path path = Paths.get(dirPattern);
        if (Files.exists(path) && Files.isDirectory(path)) {
          registerSingleDirectory(path);
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to register directory: {}", dirPattern, e);
    }
  }

  /**
   * 注册单个目录
   */
  private void registerSingleDirectory(Path path) throws IOException {
    // 递归注册所有子目录
    Files.walk(path)
        .filter(Files::isDirectory)
        .forEach(dir -> {
          try {
            WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(key, dir);
            LOG.debug("Registered directory for watching: {}", dir);
          } catch (IOException e) {
            LOG.error("Failed to register directory: {}", dir, e);
          }
        });
  }

  /**
   * 文件监控循环
   */
  private void watchLoop() {
    LOG.info("File watch loop started");

    while (watching.get()) {
      try {
        WatchKey key = watchService.take();
        Path dir = watchKeys.get(key);

        if (dir == null) {
          continue;
        }

        boolean hasClassChanges = false;

        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind<?> kind = event.kind();

          if (kind == StandardWatchEventKinds.OVERFLOW) {
            continue;
          }

          @SuppressWarnings("unchecked")
          WatchEvent<Path> ev = (WatchEvent<Path>) event;
          Path filename = ev.context();
          Path fullPath = dir.resolve(filename);

          if (filename.toString().endsWith(".class")) {
            String filePath = fullPath.toString();
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastModified.get(filePath);

            // 防止重复触发
            if (lastTime == null || (currentTime - lastTime) > 500) {
              lastModified.put(filePath, currentTime);
              hasClassChanges = true;

              LOG.info("Detected {} class file: {}",
                  kind.name().toLowerCase(),
                  getRelativePath(fullPath));
            }
          }
        }

        if (hasClassChanges) {
          scheduleReload();
        }

        boolean valid = key.reset();
        if (!valid) {
          watchKeys.remove(key);
          if (watchKeys.isEmpty()) {
            break;
          }
        }

      } catch (InterruptedException e) {
        LOG.info("File watcher interrupted");
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        LOG.error("Error in file watch loop", e);
      }
    }

    LOG.info("File watch loop stopped");
  }

  /**
   * 计划重载操作
   */
  private void scheduleReload() {
    vertx.setTimer(RELOAD_DELAY, id -> {
      LOG.warn("=== Hot Reload Triggered ===");
      LOG.warn("Class files have been modified. For changes to take effect:");
      LOG.warn("1. Restart the application, or");
      LOG.warn("2. Use IDE's hot swap feature if available");
      LOG.warn("=== Note: Full hot reload requires application restart ===");

      // 这里可以实现更复杂的热重载逻辑
      // 例如：重新加载特定的Spring Bean、清除缓存等
      notifyApplicationReload();
    });
  }

  /**
   * 通知应用程序重载
   */
  private void notifyApplicationReload() {
    try {
      // 可以在这里实现具体的重载逻辑
      // 例如：发送事件、清除缓存、重新注册路由等

      // 示例：清除Spring容器中的某些缓存
      if (applicationContext != null) {
        LOG.debug("Application context available for potential reload operations");
        // 这里可以添加具体的重载逻辑
      }

    } catch (Exception e) {
      LOG.error("Error during application reload notification", e);
    }
  }

  /**
   * 获取相对路径（用于日志显示）
   */
  private String getRelativePath(Path fullPath) {
    try {
      Path currentDir = Paths.get(".").toAbsolutePath().normalize();
      return currentDir.relativize(fullPath.toAbsolutePath().normalize()).toString();
    } catch (Exception e) {
      return fullPath.toString();
    }
  }

  /**
   * 停止文件监控
   */
  public void stopWatching() {
    if (!watching.get()) {
      return;
    }

    watching.set(false);

    if (watchService != null) {
      try {
        watchService.close();
        LOG.info("File watcher stopped");
      } catch (IOException e) {
        LOG.error("Error closing watch service", e);
      }
    }

    watchKeys.clear();
    lastModified.clear();
  }

  /**
   * 检查是否正在监控
   */
  public boolean isWatching() {
    return watching.get();
  }

  /**
   * 获取监控的目录数量
   */
  public int getWatchedDirectoriesCount() {
    return watchKeys.size();
  }
}