-- PostgreSQL 数据库初始化脚本
-- 使用方法: psql -U postgres -d myapp_db -f init-database.sql

-- 创建通用数据存储表（如果使用动态表模式）
CREATE TABLE IF NOT EXISTS data_store (
    id SERIAL PRIMARY KEY,
    collection VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(collection, document_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_data_store_collection ON data_store(collection);
CREATE INDEX IF NOT EXISTS idx_data_store_document_id ON data_store(document_id);
CREATE INDEX IF NOT EXISTS idx_data_store_data_gin ON data_store USING GIN(data);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_data_store_updated_at BEFORE UPDATE
    ON data_store FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 如果使用固定表结构，可以创建具体的表
-- 用户表示例
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(50),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 产品表示例
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2),
    stock INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 添加更新时间触发器
CREATE TRIGGER update_users_updated_at BEFORE UPDATE
    ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE
    ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 创建测试数据（可选）
-- INSERT INTO users (id, username, email, role) VALUES 
--     ('1', 'admin', 'admin@example.com', 'admin'),
--     ('2', 'john_doe', 'john@example.com', 'user');

-- 查看创建的表
\dt

-- 查看表结构
\d data_store
\d users
\d products 