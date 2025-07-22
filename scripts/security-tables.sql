-- 登录安全增强 - 数据库表结构
-- 使用方法: 在现有数据库中执行此脚本

-- 创建登录尝试限制表
CREATE TABLE IF NOT EXISTS login_attempts (
    id VARCHAR(36) PRIMARY KEY,
    identifier VARCHAR(255) NOT NULL COMMENT '登录标识符（邮箱、手机号等）',
    identifier_type ENUM('email', 'phone', 'username') NOT NULL COMMENT '标识符类型',
    client_ip VARCHAR(45) NOT NULL COMMENT '客户端IP地址',
    attempt_count INT DEFAULT 1 COMMENT '失败尝试次数',
    first_attempt_at DATETIME NOT NULL COMMENT '首次失败时间',
    last_attempt_at DATETIME NOT NULL COMMENT '最后失败时间',
    locked_until DATETIME NULL COMMENT '锁定到什么时间',
    lock_level ENUM('none', 'account', 'ip', 'global') DEFAULT 'none' COMMENT '锁定级别',
    lock_reason VARCHAR(500) NULL COMMENT '锁定原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_identifier_ip (identifier, client_ip),
    INDEX idx_identifier (identifier),
    INDEX idx_client_ip (client_ip),
    INDEX idx_locked_until (locked_until),
    INDEX idx_last_attempt (last_attempt_at),
    INDEX idx_lock_level (lock_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录尝试限制表';

-- 创建登录日志表
CREATE TABLE IF NOT EXISTS login_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NULL COMMENT '用户ID（成功登录时有值）',
    identifier VARCHAR(255) NOT NULL COMMENT '登录标识符',
    identifier_type ENUM('email', 'phone', 'username', 'google', 'wechat', 'github') NOT NULL COMMENT '标识符类型',
    login_status ENUM('success', 'failed', 'blocked', 'expired') NOT NULL COMMENT '登录状态',
    failure_reason VARCHAR(500) NULL COMMENT '失败原因',
    client_ip VARCHAR(45) NOT NULL COMMENT '客户端IP地址',
    user_agent TEXT NULL COMMENT '用户代理字符串',
    login_source VARCHAR(50) DEFAULT 'web' COMMENT '登录来源',
    session_id VARCHAR(100) NULL COMMENT '会话ID',
    location_info JSON NULL COMMENT '地理位置信息',
    device_info JSON NULL COMMENT '设备信息',
    risk_score INT DEFAULT 0 COMMENT '风险评分 0-100',
    login_duration INT NULL COMMENT '登录耗时（毫秒）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_identifier (identifier),
    INDEX idx_status (login_status),
    INDEX idx_client_ip (client_ip),
    INDEX idx_created_at (created_at),
    INDEX idx_risk_score (risk_score),
    INDEX idx_identifier_type (identifier_type),
    INDEX idx_login_source (login_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- 增强IP黑名单表
CREATE TABLE IF NOT EXISTS ip_blacklist (
    id VARCHAR(36) PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL COMMENT 'IP地址',
    ip_range VARCHAR(50) NULL COMMENT 'IP范围（CIDR格式）',
    blacklist_type ENUM('manual', 'auto', 'temporary') NOT NULL COMMENT '黑名单类型',
    reason VARCHAR(500) NOT NULL COMMENT '封禁原因',
    created_by VARCHAR(36) NULL COMMENT '创建人（管理员ID）',
    expires_at DATETIME NULL COMMENT '过期时间（NULL表示永久）',
    auto_blacklist_count INT DEFAULT 0 COMMENT '自动封禁次数',
    last_violation_at DATETIME NULL COMMENT '最后违规时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_ip (ip_address),
    INDEX idx_ip_range (ip_range),
    INDEX idx_blacklist_type (blacklist_type),
    INDEX idx_expires_at (expires_at),
    INDEX idx_created_at (created_at),
    INDEX idx_last_violation (last_violation_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP黑名单表';

-- 插入一些测试数据（可选）
-- INSERT INTO login_attempts (id, identifier, identifier_type, client_ip, attempt_count, first_attempt_at, last_attempt_at) VALUES
-- (UUID(), 'test@example.com', 'email', '192.168.1.100', 3, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 5 MINUTE); 