-- MySQL 数据库初始化脚本
-- 使用方法: mysql -u root -p myapp_db < init-database-mysql.sql

-- 创建用户表（业务实体）
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    department VARCHAR(100),
    role VARCHAR(50) DEFAULT 'user',
    status VARCHAR(20) DEFAULT 'active',
    avatar_url VARCHAR(500),
    notes TEXT,
    last_login DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_phone (phone),
    INDEX idx_status (status),
    INDEX idx_role (role),
    INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建用户账户表（认证凭证）
CREATE TABLE IF NOT EXISTS user_accounts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    account_type ENUM('email', 'google', 'wechat', 'github', 'phone') NOT NULL,
    identifier VARCHAR(255) NOT NULL,  -- 邮箱、Google ID、微信 openid 等
    credentials TEXT,                  -- 密码hash、token等（JSON格式）
    verified BOOLEAN DEFAULT FALSE,
    primary_account BOOLEAN DEFAULT FALSE,  -- 是否为主账户
    last_login DATETIME,
    registration_ip VARCHAR(45),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_account (account_type, identifier),
    INDEX idx_user_id (user_id),
    INDEX idx_account_type (account_type),
    INDEX idx_identifier (identifier),
    INDEX idx_verified (verified),
    INDEX idx_primary_account (primary_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入初始用户数据
INSERT INTO users (id, name, phone, department, role, status, notes) VALUES
('550e8400-e29b-41d4-a716-446655440001', '张三', '13800138001', '技术部', 'admin', 'active', '系统管理员'),
('550e8400-e29b-41d4-a716-446655440002', '李四', '13800138002', '销售部', 'user', 'active', '销售经理'),
('550e8400-e29b-41d4-a716-446655440003', '王五', '13800138003', '人事部', 'user', 'active', '人事专员'),
('550e8400-e29b-41d4-a716-446655440004', '赵六', '13800138004', '财务部', 'user', 'active', '财务主管'),
('550e8400-e29b-41d4-a716-446655440005', '钱七', '13800138005', '技术部', 'user', 'active', '高级开发工程师'),
('550e8400-e29b-41d4-a716-446655440006', '孙八', '13800138006', '市场部', 'user', 'inactive', '市场专员（已离职）'),
('550e8400-e29b-41d4-a716-446655440007', '周九', '13800138007', '技术部', 'user', 'active', '测试工程师'),
('550e8400-e29b-41d4-a716-446655440008', '吴十', '13800138008', '运营部', 'user', 'active', '运营总监');

-- 插入初始账户数据（邮箱账户）
INSERT INTO user_accounts (id, user_id, account_type, identifier, credentials, verified, primary_account, registration_ip) VALUES
('acc-550e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'email', 'zhangsan@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', 'email', 'lisi@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003', 'email', 'wangwu@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440004', 'email', 'zhaoliu@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440005', 'email', 'qianqi@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440006', '550e8400-e29b-41d4-a716-446655440006', 'email', 'sunba@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440007', '550e8400-e29b-41d4-a716-446655440007', 'email', 'zhoujiu@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1'),
('acc-550e8400-e29b-41d4-a716-446655440008', '550e8400-e29b-41d4-a716-446655440008', 'email', 'wushi@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewfBcrQtlO/Y5DAa', TRUE, TRUE, '127.0.0.1');

-- 创建用户权限关联表（为将来的权限管理做准备）
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id VARCHAR(36),
    permission_id VARCHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci; 