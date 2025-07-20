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