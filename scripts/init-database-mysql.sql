-- MySQL 数据库初始化脚本
-- 使用方法: mysql -u root -p myapp_db < init-database-mysql.sql

-- 创建数据库（如果需要）
-- CREATE DATABASE IF NOT EXISTS myapp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE myapp_db;

-- 创建通用数据存储表（如果使用动态表模式）
CREATE TABLE IF NOT EXISTS data_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    data JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_collection_doc(collection, document_id),
    INDEX idx_collection(collection),
    INDEX idx_document_id(document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 如果使用固定表结构，可以创建具体的表
-- 用户表示例
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(50),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username(username),
    INDEX idx_email(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 产品表示例
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2),
    stock INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建测试数据（可选）
-- INSERT INTO users (id, username, email, role) VALUES 
--     ('1', 'admin', 'admin@example.com', 'admin'),
--     ('2', 'john_doe', 'john@example.com', 'user');

-- 查看创建的表
SHOW TABLES;

-- 查看表结构
DESC data_store;
DESC users;
DESC products; 

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    department VARCHAR(100),
    role VARCHAR(50) DEFAULT 'user',
    status VARCHAR(20) DEFAULT 'active',
    last_login DATETIME,
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入初始用户数据
INSERT INTO users (id, name, email, phone, department, role, status, notes) VALUES
('550e8400-e29b-41d4-a716-446655440001', '张三', 'zhangsan@example.com', '13800138001', '技术部', 'admin', 'active', '系统管理员'),
('550e8400-e29b-41d4-a716-446655440002', '李四', 'lisi@example.com', '13800138002', '销售部', 'user', 'active', '销售经理'),
('550e8400-e29b-41d4-a716-446655440003', '王五', 'wangwu@example.com', '13800138003', '人事部', 'user', 'active', '人事专员'),
('550e8400-e29b-41d4-a716-446655440004', '赵六', 'zhaoliu@example.com', '13800138004', '财务部', 'user', 'active', '财务主管'),
('550e8400-e29b-41d4-a716-446655440005', '钱七', 'qianqi@example.com', '13800138005', '技术部', 'user', 'active', '高级开发工程师'),
('550e8400-e29b-41d4-a716-446655440006', '孙八', 'sunba@example.com', '13800138006', '市场部', 'user', 'inactive', '市场专员（已离职）'),
('550e8400-e29b-41d4-a716-446655440007', '周九', 'zhoujiu@example.com', '13800138007', '技术部', 'user', 'active', '测试工程师'),
('550e8400-e29b-41d4-a716-446655440008', '吴十', 'wushi@example.com', '13800138008', '运营部', 'user', 'active', '运营总监');

-- 创建用户权限关联表（为将来的权限管理做准备）
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id VARCHAR(36),
    permission_id VARCHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci; 