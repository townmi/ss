-- 创建数据字典表
CREATE TABLE IF NOT EXISTS data_dictionary (
    id VARCHAR(36) PRIMARY KEY COMMENT '唯一标识，UUID格式',
    category VARCHAR(50) NOT NULL COMMENT '类别，如 Currency, Country, Account, Project',
    code VARCHAR(50) NOT NULL COMMENT '类别代码，如 CNY, US, 1001, ProjA',
    name VARCHAR(100) NOT NULL COMMENT '类别名称，如 Chinese Yuan, United States',
    description TEXT NULL COMMENT '类别描述',
    attributes JSON NULL COMMENT '附加属性，存储如符号、ISO代码、科目类型等',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统内置代码',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY unique_category_code (category, code) COMMENT '确保类别内代码唯一',
    INDEX idx_category (category) COMMENT '类别查询索引',
    INDEX idx_system (is_system) COMMENT '系统内置查询索引',
    INDEX idx_active (is_active) COMMENT '启用状态查询索引',
    INDEX idx_created_at (created_at) COMMENT '创建时间查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典表，用于管理分类数据';