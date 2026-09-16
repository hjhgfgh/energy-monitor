-- MySQL8 默认 caching_sha2_password 认证要求 JDBC 侧 allowPublicKeyRetrieval=true，
-- 而 Nacos v3 镜像的默认 JDBC URL 模板不带该参数。
-- 把 root 切回 mysql_native_password，双方都能连（兼容老 JDBC URL）。
ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
FLUSH PRIVILEGES;
