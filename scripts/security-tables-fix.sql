-- 登录安全功能修复脚本
-- 移除错误的IP记录设计，优化为基于IP黑名单的方案

-- 1. 修复login_attempts表的枚举值（移除'ip'）
ALTER TABLE login_attempts 
MODIFY COLUMN identifier_type ENUM('email', 'phone', 'username') NOT NULL COMMENT '标识符类型';

-- 2. 增强ip_blacklist表结构（如果字段不存在则添加）
ALTER TABLE ip_blacklist 
ADD COLUMN IF NOT EXISTS auto_blacklist_count INT DEFAULT 0 COMMENT '自动封禁次数',
ADD COLUMN IF NOT EXISTS last_violation_at DATETIME NULL COMMENT '最后违规时间',
ADD INDEX IF NOT EXISTS idx_last_violation (last_violation_at);

-- 3. 清理错误的IP记录（identifier_type='ip'的记录）
DELETE FROM login_attempts WHERE identifier_type = 'ip' OR identifier = client_ip;

-- 4. 验证修复结果
SELECT 'login_attempts表结构修复完成' as status;
SHOW COLUMNS FROM login_attempts LIKE 'identifier_type';

SELECT 'ip_blacklist表结构增强完成' as status;  
SHOW COLUMNS FROM ip_blacklist; 