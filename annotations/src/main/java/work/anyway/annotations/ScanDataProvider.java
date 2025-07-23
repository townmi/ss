package work.anyway.annotations;

import java.util.List;
import java.util.Map;

/**
 * 扫描数据提供者接口
 * 用于在 Host 和插件之间传递扫描到的原始数据
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface ScanDataProvider {

  /**
   * 获取扫描到的菜单项原始数据
   * 
   * @return 菜单项数据列表
   */
  List<Map<String, Object>> getScannedMenuItems();

  /**
   * 获取扫描到的权限定义原始数据
   * 
   * @return 权限定义数据列表
   */
  List<Map<String, Object>> getScannedPermissions();
}