-- Docker 首次启动时由 mysql 官方镜像自动执行（挂载在 /docker-entrypoint-initdb.d）
-- 业务库
CREATE DATABASE IF NOT EXISTS energy_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- Nacos 配置库
CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
