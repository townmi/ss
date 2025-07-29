package work.anyway.packages.system.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import work.anyway.annotations.InterceptorComponent;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.RenderTemplate;
import work.anyway.annotations.TemplateProcessor;
import work.anyway.packages.system.plugin.theme.ThemeManager;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板渲染拦截器
 * 处理 @RenderTemplate 注解，自动渲染模板
 */
@InterceptorComponent(name = "TemplateRendering", description = "Automatically render templates based on @RenderTemplate annotation", order = 100 // 在其他拦截器之后执行
)
@Component
public class TemplateRenderingInterceptor implements Interceptor {

  private static final Logger LOG = LoggerFactory.getLogger(TemplateRenderingInterceptor.class);

  // 模板缓存
  private final Map<String, Mustache> templateCache = new ConcurrentHashMap<>();
  // 插件名称缓存
  private final Map<Class<?>, String> pluginNameCache = new ConcurrentHashMap<>();

  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  // 使用 setter 注入，支持可选依赖
  private TemplateProcessor templateProcessor;
  private ThemeManager themeManager;
  private ApplicationContext applicationContext;

  // 错误消息常量
  private static final String ERROR_NO_DATA = "Template data not provided";
  private static final String ERROR_NO_HANDLER = "Handler not found";
  private static final String ERROR_TEMPLATE_NOT_FOUND = "Template not found";
  private static final String ERROR_RENDERING_FAILED = "Template rendering failed";

  // 上下文键常量
  private static final String CTX_THEME_AVAILABLE = "_theme_processor_available";
  private static final String CTX_HANDLER_METHOD = "_handler_method";
  private static final String CTX_HANDLER_INSTANCE = "_handler_instance";
  private static final String CTX_HANDLER = "_handler";
  private static final String CTX_VIEW_DATA = "viewData";
  private static final String CTX_TEMPLATE_DATA = "templateData";
  private static final String CTX_RENDERED_CONTENT = "_rendered_content";
  private static final String CTX_LAYOUT = "_layout";

  @Autowired(required = false)
  @Qualifier("defaultThemeProcessor")
  public void setTemplateProcessor(TemplateProcessor templateProcessor) {
    this.templateProcessor = templateProcessor;
    LOG.debug("TemplateProcessor injected: {}", templateProcessor != null);
  }

  @Autowired(required = false)
  public void setThemeManager(ThemeManager themeManager) {
    this.themeManager = themeManager;
    LOG.debug("ThemeManager injected: {}", themeManager != null);
  }

  @Autowired
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    // 尝试获取可能通过其他方式注册的 beans
    if (this.templateProcessor == null) {
      try {
        this.templateProcessor = applicationContext.getBean("defaultThemeProcessor", TemplateProcessor.class);
      } catch (Exception e) {
        LOG.debug("No defaultThemeProcessor bean found: {}", e.getMessage());
      }
    }
  }

  @Override
  public String getName() {
    return "TemplateRendering";
  }

  @Override
  public boolean preHandle(RoutingContext ctx) {
    LOG.debug("preHandle called for path: {}", ctx.request().path());

    // 标记主题处理器是否可用
    if (templateProcessor != null) {
      ctx.put(CTX_THEME_AVAILABLE, true);
    }

    return true;
  }

  @Override
  public void postHandle(RoutingContext ctx, Object result) {
    LOG.debug("postHandle called for path: {}", ctx.request().path());

    // 检查是否有 @RenderTemplate 注解
    Method method = ctx.get(CTX_HANDLER_METHOD);
    if (method == null) {
      LOG.info("No handler method found in context");
      return;
    }

    RenderTemplate renderTemplate = method.getAnnotation(RenderTemplate.class);
    if (renderTemplate == null) {
      // 检查是否有手动设置的渲染内容
      handleManualRendering(ctx);
      return;
    }

    // 自动渲染模板
    handleAutoRendering(ctx, renderTemplate);
  }

  /**
   * 处理手动渲染（兼容现有代码）
   */
  private void handleManualRendering(RoutingContext ctx) {
    LOG.debug("handleManualRendering called for path: {}", ctx.request().path());
    String renderedContent = ctx.get(CTX_RENDERED_CONTENT);
    if (renderedContent == null) {
      return;
    }

    Map<String, Object> templateData = ctx.get(CTX_TEMPLATE_DATA);
    String layout = ctx.get(CTX_LAYOUT);

    if (shouldApplyTheme(layout)) {
      try {
        ctx.put(CTX_TEMPLATE_DATA, templateData);
        String finalContent = templateProcessor.process(renderedContent, ctx);
        sendHtmlResponse(ctx, finalContent);
      } catch (Exception e) {
        LOG.error("Failed to apply theme", e);
        sendHtmlResponse(ctx, renderedContent);
      }
    } else {
      sendHtmlResponse(ctx, renderedContent);
    }
  }

  /**
   * 处理自动渲染（使用 @RenderTemplate 注解）
   */
  private void handleAutoRendering(RoutingContext ctx, RenderTemplate annotation) {
    LOG.info("Auto-rendering template: {}", annotation.value());

    // 获取模板数据
    Map<String, Object> data = getTemplateData(ctx);
    if (data == null) {
      LOG.warn("No view data found for template: {}", annotation.value());
      sendErrorResponse(ctx, 500, ERROR_NO_DATA);
      return;
    }

    // 获取处理器实例
    Object handler = getHandlerInstance(ctx);
    if (handler == null) {
      LOG.error("No handler instance found");
      sendErrorResponse(ctx, 500, ERROR_NO_HANDLER);
      return;
    }

    // 构建模板路径
    String pluginName = getCachedPluginName(handler.getClass());
    String templatePath = buildTemplatePath(pluginName, annotation.value());

    try {
      // 渲染模板
      String content = renderTemplateWithCache(templatePath, data, handler.getClass());
      boolean shouldApplyTheme = shouldApplyTheme(annotation);
      LOG.info("shouldApplyTheme: {}, templatePath: {}", shouldApplyTheme, templatePath);
      // 应用主题或直接返回
      if (shouldApplyTheme) {
        applyThemeAndSend(ctx, content, data, annotation);
      } else {
        sendHtmlResponse(ctx, content);
      }
    } catch (Exception e) {
      LOG.error("Failed to render template: {}", templatePath, e);
      sendErrorResponse(ctx, 500, ERROR_RENDERING_FAILED);
    }
  }

  /**
   * 获取模板数据
   */
  private Map<String, Object> getTemplateData(RoutingContext ctx) {
    Map<String, Object> data = ctx.get(CTX_VIEW_DATA);
    if (data == null) {
      data = ctx.get(CTX_TEMPLATE_DATA);
    }
    return data;
  }

  /**
   * 获取处理器实例
   */
  private Object getHandlerInstance(RoutingContext ctx) {
    Object handler = ctx.get(CTX_HANDLER_INSTANCE);
    if (handler == null) {
      handler = ctx.get(CTX_HANDLER);
    }
    return handler;
  }

  /**
   * 获取缓存的插件名称
   */
  private String getCachedPluginName(Class<?> clazz) {
    return pluginNameCache.computeIfAbsent(clazz, this::extractPluginName);
  }

  /**
   * 提取插件名称
   */
  private String extractPluginName(Class<?> clazz) {
    work.anyway.annotations.Plugin plugin = clazz.getAnnotation(work.anyway.annotations.Plugin.class);
    if (plugin != null) {
      return plugin.name().toLowerCase().replace(" ", "-");
    }
    return "unknown";
  }

  /**
   * 构建模板路径
   */
  private String buildTemplatePath(String pluginName, String templateName) {
    return String.format("/%s/templates/%s.mustache", pluginName, templateName);
  }

  /**
   * 使用缓存渲染模板
   */
  private String renderTemplateWithCache(String templatePath, Map<String, Object> data, Class<?> resourceClass) {
    Mustache mustache = templateCache.computeIfAbsent(templatePath, path -> {
      try (InputStream is = resourceClass.getResourceAsStream(path)) {
        if (is == null) {
          throw new RuntimeException(ERROR_TEMPLATE_NOT_FOUND + ": " + path);
        }
        return mustacheFactory.compile(
            new InputStreamReader(is, StandardCharsets.UTF_8),
            path);
      } catch (Exception e) {
        throw new RuntimeException("Failed to compile template: " + path, e);
      }
    });

    try (StringWriter writer = new StringWriter()) {
      mustache.execute(writer, data).flush();
      return writer.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute template: " + templatePath, e);
    }
  }

  /**
   * 检查是否应该应用主题
   */
  private boolean shouldApplyTheme(RenderTemplate annotation) {
    return templateProcessor != null && themeManager != null && annotation.themed();
  }

  /**
   * 检查是否应该应用主题（手动渲染）
   */
  private boolean shouldApplyTheme(String layout) {
    return templateProcessor != null && themeManager != null && layout != null;
  }

  /**
   * 应用主题并发送响应
   */
  private void applyThemeAndSend(RoutingContext ctx, String content, Map<String, Object> data,
      RenderTemplate annotation) {
    ctx.put(CTX_TEMPLATE_DATA, data);
    ctx.put(CTX_LAYOUT, annotation.layout());
    ctx.put(CTX_RENDERED_CONTENT, content);

    LOG.debug("Applying theme - content length before: {}", content.length());
    LOG.debug("Template processor: {}", templateProcessor.getClass().getName());
    LOG.debug("Theme manager available: {}", themeManager != null);
    LOG.debug("Layout: {}", annotation.layout());
    LOG.debug("Themed: {}", annotation.themed());

    try {
      String finalContent = templateProcessor.process(content, ctx);
      LOG.debug("Theme applied - content length after: {}", finalContent.length());

      // 如果内容没有变化，记录警告
      if (finalContent.equals(content)) {
        LOG.warn("Theme processor returned unchanged content!");
      }

      sendHtmlResponse(ctx, finalContent);
    } catch (Exception e) {
      LOG.error("Failed to apply theme", e);
      // 发送原始内容，但记录错误
      sendHtmlResponse(ctx, content);
    }
  }

  /**
   * 发送 HTML 响应
   */
  private void sendHtmlResponse(RoutingContext ctx, String content) {
    ctx.response()
        .putHeader("content-type", "text/html; charset=utf-8")
        .end(content);
  }

  /**
   * 发送错误响应
   */
  private void sendErrorResponse(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "text/plain; charset=utf-8")
        .end(message);
  }

  @Override
  public void afterCompletion(RoutingContext ctx, Exception ex) {
    if (ex != null) {
      LOG.debug("Exception occurred during request processing: {}", ex.getMessage());
    }

    // 清理临时数据
    ctx.remove(CTX_RENDERED_CONTENT);
    ctx.remove(CTX_LAYOUT);
    ctx.remove(CTX_HANDLER_METHOD);
    ctx.remove(CTX_HANDLER_INSTANCE);
    ctx.remove(CTX_THEME_AVAILABLE);
  }

  /**
   * 清除模板缓存（用于开发环境）
   */
  public void clearTemplateCache() {
    templateCache.clear();
    LOG.info("Template cache cleared");
  }

  /**
   * 获取缓存统计信息
   */
  public Map<String, Integer> getCacheStats() {
    return Map.of(
        "templateCacheSize", templateCache.size(),
        "pluginNameCacheSize", pluginNameCache.size());
  }
}