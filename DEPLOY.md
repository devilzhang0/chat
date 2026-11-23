# MyChat 服务器部署操作手册（前后端 + 数据库同机）

本文档面向「一台 Linux 服务器同时部署前端、后端、MySQL」的场景。目标操作系统为 Alibaba Cloud Linux（RedHat 系，使用 `yum`/`dnf`，无 `apt`）。

## 总体架构

```
浏览器 ── HTTP/HTTPS ──▶ Nginx (80/443)
                            ├─ /        → 前端静态资源（Vue 构建产物 dist/）
                            ├─ /api/**  → 反向代理 → Spring Boot (:8080)
                            └─ /ws      → WebSocket 反向代理 → Spring Boot (:8080)
                                                      │
                                                      └─ MySQL (:3306，仅本机访问)
```

- 对外只开放 **80/443**；后端 `8080` 和 MySQL `3306` 不对公网开放。
- 前端静态资源由 Nginx 直接服务，API/WS 走同域名反代，避免跨域。
- Nginx 反代时设置 `X-Forwarded-For`，后端才能记录访客真实 IP（否则只会记到 `127.0.0.1`/`::1`）。

## 一、安装环境

> Alibaba Cloud Linux 属 RedHat 系，用 `yum`/`dnf`（没有 `apt`）。下面用 `yum`（在 dnf 系统上是别名，两者等价）。MySQL 已装好的话第 3 步可跳过。

```bash
# 1. JDK 21（后端必需；-devel 是完整 JDK，含 javac）
sudo yum install -y java-21-openjdk-devel
java -version          # 确认 21
#    若提示 No match，先查可用版本：yum search openjdk | grep -i 21

# 2. Node.js ≥ 20.19（前端构建用，建议 22 LTS）
curl -fsSL https://rpm.nodesource.com/setup_22.x | sudo bash -
sudo yum install -y nodejs
node -v && npm -v

# 3. MySQL 8.0（已安装可跳过）
#    需全新安装时：先加官方 yum 源（按系统选 el7/el8/el9），再装
#    sudo yum install -y https://dev.mysql.com/get/mysql80-community-release-el8-1.noarch.rpm
#    sudo yum install -y mysql-community-server
#    sudo systemctl enable --now mysqld

# 4. Nginx
sudo yum install -y nginx
sudo systemctl enable --now nginx
```

## 二、初始化数据库

```bash
sudo mysql
```

```sql
-- 建库（库名可自定义，此处用 test，与项目默认一致）
CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4;

-- 建议：建独立账号并授权（不要用 root 跑业务）
CREATE USER 'mychat'@'localhost' IDENTIFIED BY '改成强密码';
GRANT ALL PRIVILEGES ON test.* TO 'mychat'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

然后把仓库里 `src/main/resources/db/init.sql` 导入：

```bash
mysql -u mychat -p test < src/main/resources/db/init.sql
# 校验
mysql -u mychat -p test -e "SHOW TABLES;"
# 应看到 user、message 两张表
```

> `init.sql` 是「从零建表」；`schema.sql` 是给「已有 user 表的旧库加列」的迁移脚本，二者二选一，新机器用 `init.sql`。

## 三、部署后端

### 3.1 打包（在本地或有 Maven/JDK21 的机器上执行）

```bash
cd /path/to/MyChat
./mvnw clean package -DskipTests
# 产物：target/MyChat-0.0.1-SNAPSHOT.jar
```

> `-DskipTests` 是必须的：`MybatisTest` 断言了硬编码的 DB 数据量，在干净库上会失败。

### 3.2 上传并配置

```bash
sudo mkdir -p /opt/mychat
# 把 jar 传到服务器
scp target/MyChat-0.0.1-SNAPSHOT.jar user@服务器IP:/tmp/
sudo mv /tmp/MyChat-0.0.1-SNAPSHOT.jar /opt/mychat/
```

数据库连接不用改 jar，用环境变量覆盖即可（`application.yml` 里的 datasource 仍指向公网测试库，务必覆盖成本机地址）：

### 3.3 注册为 systemd 服务

```bash
sudo tee /etc/systemd/system/mychat.service > /dev/null <<'EOF'
[Unit]
Description=MyChat Spring Boot Backend
After=network.target

[Service]
# 运行用户：www-data 是 Debian/Ubuntu 专用，RedHat 系不存在。
# 这里直接用 root（演示环境足够）；生产建议自建专用低权限用户。
User=root
WorkingDirectory=/opt/mychat
ExecStart=/usr/bin/java -jar /opt/mychat/MyChat-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

# 覆盖数据库连接（指向本机 MySQL）
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
Environment=SPRING_DATASOURCE_USERNAME=mychat
Environment=SPRING_DATASOURCE_PASSWORD=改成你的数据库密码

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now mychat
sudo systemctl status mychat
```

验证后端已启动：

```bash
curl http://127.0.0.1:8080/api/auth/me   # 应返回 JSON（ApiResult）
```

日志：`journalctl -u mychat -f`

## 四、部署前端

### 4.1 构建

```bash
cd /path/to/MyChat/frontend
npm install
npm run build
# 产物：dist/
```

前端 WS/API 都是相对路径（`/ws`、`/api`），构建产物不绑定域名，无需额外环境变量。

### 4.2 上传静态文件

```bash
sudo mkdir -p /var/www/mychat
scp -r dist/* user@服务器IP:/tmp/mychat-dist/
sudo rm -rf /var/www/mychat/*
sudo cp -r /tmp/mychat-dist/* /var/www/mychat/
```

## 五、配置 Nginx

```bash
sudo tee /etc/nginx/conf.d/mychat.conf > /dev/null <<'EOF'
server {
    listen 80;
    server_name 47.100.234.220;

    root /var/www/mychat;
    index index.html;

    # 前端单页应用
    location / {
        try_files $uri $uri/ /index.html;
    }

    # REST API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 反向代理
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
    }
}
EOF

sudo nginx -t          # 校验配置
sudo systemctl reload nginx
```

> `proxy_set_header X-Forwarded-For` 是关键：后端 `WebSocketConfig.clientIp()` 优先读这个头，这样 `user.ip` 才能记到访客真实 IP，而不是 `127.0.0.1`/`::1`。

## 六、放行防火墙

Alibaba Cloud Linux 默认用 `firewalld`：

```bash
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https   # 若配了 HTTPS
sudo firewall-cmd --reload
# 不要放行 8080 / 3306
```

> 重要：阿里云还有一层「安全组」在控制台（ECS 实例 → 安全组 → 入方向规则），需要在那里也放行 80/443（以及 22 SSH）。云控制台安全组 + 系统内 firewalld **两层都要放行**，外网才访问得到。

## 七、验证

1. 浏览器访问 `http://你的域名或IP/`，标签页标题应为 **Chat**。
2. 输入昵称进入聊天室，能看到在线列表与历史消息。
3. 开两个浏览器/设备互发消息，验证实时广播。
4. 连发 >10 条/10 秒，应提示「发送频繁，请稍后再试」。
5. 进数据库确认 `user.ip` 记录的是访客真实 IP：

   ```bash
   mysql -u mychat -p test -e "SELECT id, nickname, ip, last_seen_at FROM user ORDER BY last_seen_at DESC LIMIT 10;"
   ```

## 八、更新与重启

```bash
# 更新后端：重新打包 → 替换 jar → 重启
sudo systemctl restart mychat

# 更新前端：重新 build → 替换 /var/www/mychat →（无需重启后端）
sudo systemctl reload nginx
```

## 九、安全加固（上线前必做）

1. **改数据库密码**：`application.yml` 里硬编码了 `Zhang@2023`，务必换成强密码（用 3.3 的环境变量覆盖）。
2. **改 JWT secret**：`application.yml` 里的 `jwt.secret` 是硬编码示例值，仅 admin 演示登录使用，建议替换。
3. **只开 80/443**，后端 `8080` 与 MySQL `3306` 绑定本机即可。
4. 建议上 HTTPS（`certbot --nginx` 一键配置 Let's Encrypt），否则 `guest_id` cookie 与消息明文传输。
