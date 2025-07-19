package work.anyway.interfaces.plugin;

import io.vertx.ext.web.Router;

/**
 * Plugin interface for dynamic loading in Vert.x host
 */
public interface Plugin {

  /**
   * Get the plugin name
   * 
   * @return plugin name
   */
  String getName();

  /**
   * Get the plugin version
   * 
   * @return plugin version
   */
  String getVersion();

  /**
   * Get the plugin description
   * 
   * @return plugin description
   */
  default String getDescription() {
    return "No description provided";
  }

  /**
   * Get the plugin icon (emoji or icon class)
   * 
   * @return plugin icon
   */
  default String getIcon() {
    return "📦";
  }

  /**
   * Get the plugin main page path
   * 
   * @return plugin main page path, null if no page
   */
  default String getMainPagePath() {
    return null;
  }

  /**
   * Initialize the plugin and register routes
   * 
   * @param router Vert.x router to register routes
   */
  void initialize(Router router);

  /**
   * Initialize the plugin with service registry
   * 
   * @param router          Vert.x router to register routes
   * @param serviceRegistry optional service registry for dependency lookup
   */
  default void initialize(Router router, ServiceRegistry serviceRegistry) {
    // By default, just call the simple version
    initialize(router);
  }
}