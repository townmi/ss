package work.anyway.packages.system.plugin.theme;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import work.anyway.annotations.InterceptorComponent;
import work.anyway.annotations.TemplateProcessor;
import work.anyway.interfaces.system.MenuItemInfo;
import work.anyway.interfaces.system.MenuService;

import java.util.*;

/**
 * 默认主题处理器
 * 负责向模板注入全局数据，如菜单、用户信息等
 */
@Component("defaultThemeProcessor")
@InterceptorComponent(name = "DefaultThemeProcessor", description = "默认主题处理器", order = 100)
public class DefaultThemeProcessor implements TemplateProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultThemeProcessor.class);

  @Autowired
  private MenuService menuService;

  @Autowired
  private ThemeManager themeManager;

  @Override
  public String process(String content, RoutingContext ctx) {
    LOG.debug("DefaultThemeProcessor.process called, path: {}", ctx.request().path());

    // 如果不是页面请求，直接返回
    if (!shouldProcess(ctx)) {
      LOG.debug("Should not process, returning original content");
      return content;
    }

    LOG.debug("Processing template with theme");

    try {
      // 获取模板数据
      Map<String, Object> templateData = getOrCreateTemplateData(ctx);
      LOG.debug("Template data keys: {}", templateData.keySet());

      // 注入全局数据
      injectGlobalData(templateData, ctx);

      // 应用主题布局
      return applyThemeLayout(content, templateData, ctx);

    } catch (Exception e) {
      LOG.error("Failed to process template", e);
      return content;
    }
  }

  /**
   * 判断是否需要处理
   */
  private boolean shouldProcess(RoutingContext ctx) {
    // 检查是否有用户登录（只有登录用户才需要菜单）
    String userId = ctx.get("userId");
    if (userId == null) {
      LOG.debug("No authenticated user, skipping menu processing");
      // return false;
    }

    // 检查是否有渲染内容标记
    String renderedContent = ctx.get("_rendered_content");
    if (renderedContent != null) {
      return true;
    }

    // 检查是否有模板数据
    Map<String, Object> templateData = ctx.get("templateData");
    if (templateData != null) {
      return true;
    }

    // 检查是否有视图数据（来自 @RenderTemplate）
    Map<String, Object> viewData = ctx.get("viewData");
    if (viewData != null) {
      return true;
    }

    return false;
  }

  /**
   * 获取或创建模板数据
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> getOrCreateTemplateData(RoutingContext ctx) {
    Object data = ctx.get("templateData");
    if (data instanceof Map) {
      return (Map<String, Object>) data;
    }

    Map<String, Object> templateData = new HashMap<>();
    ctx.put("templateData", templateData);
    return templateData;
  }

  /**
   * 注入全局数据
   */
  private void injectGlobalData(Map<String, Object> data, RoutingContext ctx) {
    // 只处理菜单相关的数据，其他交给 ThemeManager
    String userId = ctx.get("userId");
    if (userId != null) {
      // 获取用户菜单
      try {
        List<MenuItemInfo> menuTree = menuService.getUserMenuTree(userId);
        data.put("menus", convertMenuTree(menuTree, ctx.request().path()));
        LOG.debug("Loaded {} menu items for user: {}", menuTree.size(), userId);
      } catch (Exception e) {
        LOG.error("Failed to load user menu for user: {}", userId, e);
        data.put("menus", Collections.emptyList());
      }
    } else {
      data.put("menus", Collections.emptyList());
      LOG.debug("No user authenticated, empty menu");
    }

    // 面包屑 - 使用更详细的面包屑逻辑
    data.put("breadcrumb", generateBreadcrumb(ctx));
  }

  /**
   * 转换菜单树为模板格式
   */
  private List<Map<String, Object>> convertMenuTree(List<MenuItemInfo> menuTree, String currentPath) {
    List<Map<String, Object>> result = new ArrayList<>();

    for (MenuItemInfo item : menuTree) {
      Map<String, Object> menuItem = new HashMap<>();
      menuItem.put("id", item.getId());
      menuItem.put("title", item.getTitle());
      menuItem.put("path", item.getPath());
      menuItem.put("icon", item.getIcon() != null ? item.getIcon() : "📄");
      menuItem.put("isActive", isMenuActive(item, currentPath));
      menuItem.put("isTopLevel", item.getParentId() == null);

      // 处理子菜单
      if (item.getChildren() != null && !item.getChildren().isEmpty()) {
        menuItem.put("hasChildren", true);
        menuItem.put("children", convertMenuTree(new ArrayList<>(item.getChildren()), currentPath));
      } else {
        menuItem.put("hasChildren", false);
      }

      result.add(menuItem);
    }

    return result;
  }

  /**
   * 判断菜单是否激活
   */
  private boolean isMenuActive(MenuItemInfo item, String currentPath) {
    if (item.getPath() != null && currentPath.startsWith(item.getPath())) {
      return true;
    }

    // 检查子菜单
    if (item.getChildren() != null) {
      for (MenuItemInfo child : item.getChildren()) {
        if (isMenuActive(child, currentPath)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * 生成面包屑
   */
  private List<Map<String, Object>> generateBreadcrumb(RoutingContext ctx) {
    List<Map<String, Object>> breadcrumb = new ArrayList<>();

    // 首页
    breadcrumb.add(Map.of(
        "title", "首页",
        "path", "/page/",
        "isActive", false));

    // 根据路径生成面包屑
    String path = ctx.request().path();
    if (path.startsWith("/page/") && !path.equals("/page/")) {
      String[] parts = path.substring(6).split("/");
      StringBuilder currentPath = new StringBuilder("/page");

      for (int i = 0; i < parts.length; i++) {
        if (!parts[i].isEmpty()) {
          currentPath.append("/").append(parts[i]);

          Map<String, Object> item = new HashMap<>();
          item.put("title", formatTitle(parts[i]));
          item.put("path", currentPath.toString());
          item.put("isActive", i == parts.length - 1);
          breadcrumb.add(item);
        }
      }
    }

    return breadcrumb;
  }

  /**
   * 格式化标题
   */
  private String formatTitle(String segment) {
    // 简单的标题格式化
    return segment.substring(0, 1).toUpperCase() + segment.substring(1);
  }

  /**
   * 应用主题布局
   */
  private String applyThemeLayout(String content, Map<String, Object> data, RoutingContext ctx) {
    // 获取布局名称
    String layoutName = (String) ctx.get("_layout");
    if (layoutName == null) {
      layoutName = "base";
    }

    LOG.debug("Applying theme layout - theme: default, layout: {}", layoutName);
    LOG.debug("Content length before theme: {}", content.length());

    // 设置内容
    data.put("content", content);

    // 使用主题管理器渲染
    String result = themeManager.renderWithTheme("default", layoutName, data, ctx);
    LOG.debug("Content length after theme: {}", result.length());

    return result;
  }

  @Override
  public int getOrder() {
    return 100;
  }
}