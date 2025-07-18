package work.anyway.api.plugin;

import java.util.Optional;

/**
 * Service registry for plugins to lookup services
 */
public interface ServiceRegistry {
  
  /**
   * Register a service implementation
   * @param serviceClass the service interface class
   * @param implementation the service implementation
   * @param <T> the service type
   */
  <T> void register(Class<T> serviceClass, T implementation);
  
  /**
   * Lookup a service by its interface
   * @param serviceClass the service interface class
   * @param <T> the service type
   * @return the service implementation if found
   */
  <T> Optional<T> lookup(Class<T> serviceClass);
  
  /**
   * Get a required service by its interface
   * @param serviceClass the service interface class
   * @param <T> the service type
   * @return the service implementation
   * @throws IllegalStateException if service not found
   */
  default <T> T require(Class<T> serviceClass) {
    return lookup(serviceClass)
        .orElseThrow(() -> new IllegalStateException(
            "Required service not found: " + serviceClass.getName()));
  }
} 