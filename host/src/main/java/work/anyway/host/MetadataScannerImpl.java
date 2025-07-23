package work.anyway.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import work.anyway.annotations.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 注解扫描器
 * 仅负责扫描注解并收集原始数据，不依赖任何业务接口
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
public class MetadataScannerImpl implements BeanPostProcessor, ScanDataProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataScannerImpl.class);

  /**
   * 扫描到的菜单原始数据
   */
  private final List<Map<String, Object>> scannedMenuItems = new ArrayList<>();

  /**
   * 扫描到的权限原始数据
   */
  private final List<Map<String, Object>> scannedPermissions = new ArrayList<>();

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> targetClass = AopUtils.getTargetClass(bean);

    // 获取插件信息
    Plugin plugin = targetClass.getAnnotation(Plugin.class);
    String pluginName = plugin != null ? plugin.name() : "System";
    String pluginVersion = plugin != null ? plugin.version() : "1.0.0";

    // 扫描权限定义
    scanPermissions(targetClass, pluginName, pluginVersion);

    // 扫描菜单项
    scanMenuItems(targetClass, bean, pluginName, pluginVersion);

    return bean;
  }

  /**
   * 扫描权限定义
   */
  private void scanPermissions(Class<?> clazz, String pluginName, String pluginVersion) {
    // 扫描 @PermissionDef 注解
    PermissionDef[] defs = clazz.getAnnotationsByType(PermissionDef.class);
    for (PermissionDef def : defs) {
      Map<String, Object> permissionData = new HashMap<>();
      permissionData.put("code", def.code());
      permissionData.put("name", def.name());
      permissionData.put("description", def.description());
      permissionData.put("defaultRoles", Arrays.asList(def.defaultRoles()));
      permissionData.put("pluginName", pluginName);
      permissionData.put("pluginVersion", pluginVersion);

      scannedPermissions.add(permissionData);
      LOG.debug("Scanned permission: {} from plugin: {}",
          def.code(), pluginName);
    }
  }

  /**
   * 扫描菜单项
   */
  private void scanMenuItems(Class<?> clazz, Object bean, String pluginName, String pluginVersion) {
    // 扫描类级别的菜单
    scanClassLevelMenuItems(clazz, pluginName, pluginVersion);

    // 扫描方法级别的菜单
    scanMethodLevelMenuItems(clazz, bean, pluginName, pluginVersion);
  }

  /**
   * 扫描类级别的菜单项
   */
  private void scanClassLevelMenuItems(Class<?> clazz, String pluginName, String pluginVersion) {
    MenuItem[] items = clazz.getAnnotationsByType(MenuItem.class);
    String defaultPath = getDefaultPath(clazz);

    for (MenuItem item : items) {
      String menuId = item.id().isEmpty() ? clazz.getSimpleName() : item.id();
      String path = item.path().isEmpty() ? defaultPath : item.path();

      Map<String, Object> menuData = new HashMap<>();
      menuData.put("id", menuId);
      menuData.put("title", item.title());
      menuData.put("path", path);
      menuData.put("parentId", item.parentId());
      menuData.put("icon", item.icon());
      menuData.put("order", item.order());
      menuData.put("permissions", Arrays.asList(item.permissions()));
      menuData.put("anyPermissions", Arrays.asList(item.anyPermissions()));
      menuData.put("visible", item.visible());
      menuData.put("type", item.type());
      menuData.put("target", item.target());
      menuData.put("pluginName", pluginName);
      menuData.put("pluginVersion", pluginVersion);

      scannedMenuItems.add(menuData);
      LOG.debug("Scanned class-level menu: {} from plugin: {}",
          menuId, pluginName);
    }
  }

  /**
   * 扫描方法级别的菜单项
   */
  private void scanMethodLevelMenuItems(Class<?> clazz, Object bean, String pluginName, String pluginVersion) {
    Method[] methods = clazz.getDeclaredMethods();

    for (Method method : methods) {
      MenuItem[] items = method.getAnnotationsByType(MenuItem.class);

      for (MenuItem item : items) {
        String menuId = item.id().isEmpty()
            ? clazz.getSimpleName() + "." + method.getName()
            : item.id();
        String path = item.path().isEmpty()
            ? getMethodPath(clazz, method)
            : item.path();

        Map<String, Object> menuData = new HashMap<>();
        menuData.put("id", menuId);
        menuData.put("title", item.title());
        menuData.put("path", path);
        menuData.put("parentId", item.parentId());
        menuData.put("icon", item.icon());
        menuData.put("order", item.order());
        menuData.put("permissions", Arrays.asList(item.permissions()));
        menuData.put("anyPermissions", Arrays.asList(item.anyPermissions()));
        menuData.put("visible", item.visible());
        menuData.put("type", item.type());
        menuData.put("target", item.target());
        menuData.put("pluginName", pluginName);
        menuData.put("pluginVersion", pluginVersion);

        scannedMenuItems.add(menuData);
        LOG.debug("Scanned method-level menu: {} from plugin: {}",
            menuId, pluginName);
      }
    }
  }

  /**
   * 获取类的默认路径
   */
  private String getDefaultPath(Class<?> clazz) {
    RequestMapping mapping = clazz.getAnnotation(RequestMapping.class);
    if (mapping != null) {
      String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
      if (paths.length > 0) {
        return paths[0];
      }
    }
    return "";
  }

  /**
   * 获取方法的路径
   */
  private String getMethodPath(Class<?> clazz, Method method) {
    String basePath = getDefaultPath(clazz);

    // 检查 @GetMapping
    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    if (getMapping != null) {
      String[] paths = getMapping.value().length > 0 ? getMapping.value() : getMapping.path();
      if (paths.length > 0) {
        return combinePaths(basePath, paths[0]);
      }
    }

    // 检查 @PostMapping
    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    if (postMapping != null) {
      String[] paths = postMapping.value().length > 0 ? postMapping.value() : postMapping.path();
      if (paths.length > 0) {
        return combinePaths(basePath, paths[0]);
      }
    }

    // 检查 @RequestMapping
    RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
    if (requestMapping != null) {
      String[] paths = requestMapping.value().length > 0 ? requestMapping.value() : requestMapping.path();
      if (paths.length > 0) {
        return combinePaths(basePath, paths[0]);
      }
    }

    return basePath;
  }

  /**
   * 组合路径
   */
  private String combinePaths(String basePath, String subPath) {
    if (basePath.isEmpty()) {
      return subPath;
    }
    if (subPath.isEmpty()) {
      return basePath;
    }

    // 确保路径正确拼接
    if (basePath.endsWith("/") && subPath.startsWith("/")) {
      return basePath + subPath.substring(1);
    } else if (!basePath.endsWith("/") && !subPath.startsWith("/")) {
      return basePath + "/" + subPath;
    }

    return basePath + subPath;
  }

  /**
   * 获取扫描到的菜单数据
   * 
   * @return 菜单原始数据列表
   */
  public List<Map<String, Object>> getScannedMenuItems() {
    return new ArrayList<>(scannedMenuItems);
  }

  /**
   * 获取扫描到的权限数据
   * 
   * @return 权限原始数据列表
   */
  public List<Map<String, Object>> getScannedPermissions() {
    return new ArrayList<>(scannedPermissions);
  }
}