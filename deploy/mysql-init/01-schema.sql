-- 真实时薪 · 数据库表结构（MySQL 8）—— 容器首次启动时执行
CREATE TABLE IF NOT EXISTS settings (
    id   INT  NOT NULL COMMENT '固定为 1（单行配置）',
    data TEXT NOT NULL COMMENT '设置项 JSON 字符串',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='全局设置';

CREATE TABLE IF NOT EXISTS records (
    date  VARCHAR(10) NOT NULL COMMENT '日期 YYYY-MM-DD',
    start VARCHAR(5)  NOT NULL COMMENT '实际上班 HH:mm',
    end   VARCHAR(5)  NOT NULL DEFAULT '' COMMENT '实际下班 HH:mm',
    rest  INT         NOT NULL DEFAULT 0 COMMENT '当日自定义休息分钟',
    PRIMARY KEY (date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每日打卡记录';

CREATE TABLE IF NOT EXISTS kv (
    `key` VARCHAR(64) NOT NULL COMMENT '键',
    value TEXT        NOT NULL COMMENT '值',
    PRIMARY KEY (`key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通用键值存储';
