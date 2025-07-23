-- 权限管理系统 - 数据库表结构
-- 使用方法: 在现有数据库中执行此脚本

-- 创建权限定义表
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL COMMENT '权限代码，如 user.create',
    name VARCHAR(200) NOT NULL COMMENT '权限名称',
    description TEXT NULL COMMENT '权限描述',
    category VARCHAR(50) NULL COMMENT '权限分类',
    plugin_name VARCHAR(100) NULL COMMENT '所属插件名称',
    plugin_version VARCHAR(20) NULL COMMENT '插件版本',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_code (code),
    INDEX idx_category (category),
    INDEX idx_plugin (plugin_name),
    INDEX idx_active (is_active),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限定义表';

-- 创建角色表
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(50) NOT NULL COMMENT '角色代码，如 admin, user',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description TEXT NULL COMMENT '角色描述',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统内置角色',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_code (code),
    INDEX idx_system (is_system),
    INDEX idx_active (is_active),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 创建角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id VARCHAR(36) PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL COMMENT '角色ID',
    permission_id VARCHAR(36) NOT NULL COMMENT '权限ID',
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    granted_by VARCHAR(36) NULL COMMENT '授权人ID',
    
    UNIQUE KEY unique_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id),
    INDEX idx_granted_at (granted_at),
    
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 创建用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    role_id VARCHAR(36) NOT NULL COMMENT '角色ID',
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    assigned_by VARCHAR(36) NULL COMMENT '分配人ID',
    expires_at DATETIME NULL COMMENT '过期时间（NULL表示永久）',
    
    UNIQUE KEY unique_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id),
    INDEX idx_assigned_at (assigned_at),
    INDEX idx_expires_at (expires_at),
    
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 创建用户权限表（用于直接授权，绕过角色）
CREATE TABLE IF NOT EXISTS user_permissions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    permission_id VARCHAR(36) NOT NULL COMMENT '权限ID',
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    granted_by VARCHAR(36) NULL COMMENT '授权人ID',
    expires_at DATETIME NULL COMMENT '过期时间（NULL表示永久）',
    
    UNIQUE KEY unique_user_permission (user_id, permission_id),
    INDEX idx_user (user_id),
    INDEX idx_permission (permission_id),
    INDEX idx_granted_at (granted_at),
    INDEX idx_expires_at (expires_at),
    
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户权限表';

-- 插入默认角色
INSERT INTO roles (id, code, name, description, is_system) VALUES
    (UUID(), 'admin', '系统管理员', '拥有系统所有权限', TRUE),
    (UUID(), 'user', '普通用户', '普通用户默认角色', TRUE),
    (UUID(), 'guest', '访客', '未登录用户的默认角色', TRUE)
ON DUPLICATE KEY UPDATE 
    name = VALUES(name),
    description = VALUES(description);

-- 创建权限审计日志表（可选）
CREATE TABLE IF NOT EXISTS permission_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    action ENUM('grant', 'revoke', 'expire') NOT NULL COMMENT '操作类型',
    target_type ENUM('user', 'role') NOT NULL COMMENT '目标类型',
    target_id VARCHAR(36) NOT NULL COMMENT '目标ID（用户或角色）',
    permission_id VARCHAR(36) NULL COMMENT '权限ID',
    role_id VARCHAR(36) NULL COMMENT '角色ID（当操作用户角色时）',
    operator_id VARCHAR(36) NOT NULL COMMENT '操作人ID',
    reason TEXT NULL COMMENT '操作原因',
    metadata JSON NULL COMMENT '额外元数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_action (action),
    INDEX idx_target (target_type, target_id),
    INDEX idx_operator (operator_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限审计日志表'; 