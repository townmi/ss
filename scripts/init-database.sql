-- PostgreSQL 数据库初始化脚本
-- 使用方法: psql -U postgres -d myapp_db -f init-database.sql

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    department VARCHAR(100),
    role VARCHAR(50) DEFAULT 'user',
    status VARCHAR(20) DEFAULT 'active',
    last_login TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_users_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_users_timestamp
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_users_updated_at();

-- 插入初始用户数据
INSERT INTO users (id, name, email, phone, department, role, status, notes) VALUES
('550e8400-e29b-41d4-a716-446655440001', '张三', 'zhangsan@example.com', '13800138001', '技术部', 'admin', 'active', '系统管理员'),
('550e8400-e29b-41d4-a716-446655440002', '李四', 'lisi@example.com', '13800138002', '销售部', 'user', 'active', '销售经理'),
('550e8400-e29b-41d4-a716-446655440003', '王五', 'wangwu@example.com', '13800138003', '人事部', 'user', 'active', '人事专员'),
('550e8400-e29b-41d4-a716-446655440004', '赵六', 'zhaoliu@example.com', '13800138004', '财务部', 'user', 'active', '财务主管'),
('550e8400-e29b-41d4-a716-446655440005', '钱七', 'qianqi@example.com', '13800138005', '技术部', 'user', 'active', '高级开发工程师'),
('550e8400-e29b-41d4-a716-446655440006', '孙八', 'sunba@example.com', '13800138006', '市场部', 'user', 'inactive', '市场专员（已离职）'),
('550e8400-e29b-41d4-a716-446655440007', '周九', 'zhoujiu@example.com', '13800138007', '技术部', 'user', 'active', '测试工程师'),
('550e8400-e29b-41d4-a716-446655440008', '吴十', 'wushi@example.com', '13800138008', '运营部', 'user', 'active', '运营总监')
ON CONFLICT (id) DO NOTHING;

-- 创建用户权限关联表（为将来的权限管理做准备）
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id VARCHAR(36),
    permission_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
); 