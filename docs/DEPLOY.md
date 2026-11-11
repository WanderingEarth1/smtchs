# 部署文档

> 版本：V2.0
> 更新：2026-11-10

---

## 一、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | 若依 3.9.2 要求 Java 17 |
| Maven | 3.6+ | 构建后端 |
| MySQL | 8.0+ | 字符集 `utf8mb4` |
| Redis | 5.0+ | 若依用于缓存与 token |
| Node.js | 18+ | 构建前端 |
| Nginx | 1.18+ | 生产部署（可选） |

> **版本**：本项目基于 **RuoYi-Vue v3.9.2（Spring Boot 4.1.2 + Java 17）**，
> 使用 `jakarta.servlet` 而非 `javax.servlet`。

---

## 二、目录结构

```
wh-wq-smtchs/
├── backend/                    # 后端
│   ├── pom.xml                 # 父 POM
│   ├── ruoyi-admin/            # 启动模块 + Controller
│   ├── ruoyi-smtchs/           # 业务模块
│   └── sql/                    # 数据库脚本
│       ├── smtchs_schema.sql   # 建表
│       ├── smtchs_data.sql     # 初始化数据
│       ├── smtchs_menu.sql     # 菜单权限
│       └── smtchs_rollback.sql # 回滚
├── src/                        # 员工端 H5 + 管理端移动（Vue3）
├── frontend-admin/             # 管理端 PC（若依 Vue3）
└── docs/                       # 文档
```

---

## 三、数据库初始化

### 3.1 创建数据库

```sql
CREATE DATABASE ruoyi_smtchs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 3.2 导入若依基础表

从若依官方仓库获取 `sql/ry_20240629.sql`（或对应版本），导入：

```bash
mysql -u root -p ruoyi_smtchs < ry_20240629.sql
```

### 3.3 导入业务表

```bash
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_schema.sql
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_data.sql
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_menu.sql
```

> `smtchs_menu.sql` 设计为**只执行一次**。`sys_menu` 没有针对 menu_name 的唯一约束，
> 重复执行会静默插入重复菜单（不会报错），导致左侧菜单出现重复项。
> 重跑前请先执行脚本头部的清理 SQL。

### 3.4 关联员工与若依用户

`smtchs_data.sql` 插入的员工 `user_id` 为 NULL。需要手动关联：

```sql
-- 1. 在若依「系统管理 → 用户管理」创建 4 个用户（zhangsan/lisi/wangwu/zhaoliu）
-- 2. 关联到业务表
UPDATE smt_employee e
INNER JOIN sys_user u ON u.user_name = e.account
SET e.user_id = u.user_id;
```

### 3.5 验证

```sql
SELECT COUNT(*) FROM smt_employee;      -- 应为 4
SELECT COUNT(*) FROM smt_work_type;     -- 应为 5
SELECT COUNT(*) FROM smt_price_rule;    -- 应为 20
SELECT COUNT(*) FROM smt_work_record;   -- 应为 18
SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'smtchs:%';  -- 应为 24（7 菜单 + 17 按钮）
```

---

## 四、后端部署

### 4.1 后端结构

`backend/` 就是**完整的若依 v3.9.2 源码**，已内置：

```
backend/
├── pom.xml              ← 若依父 POM（已在 <modules> 注册 ruoyi-smtchs）
├── ruoyi-common/        ← 若依框架（原样保留）
├── ruoyi-framework/     ← 若依框架（原样保留）
├── ruoyi-system/        ← 若依框架（原样保留）
├── ruoyi-quartz/        ← 若依框架（原样保留）
├── ruoyi-generator/     ← 若依框架（原样保留）
├── ruoyi-smtchs/        ← 业务模块（本项目新增）
├── ruoyi-admin/         ← 启动模块
│   ├── src/main/java/com/ruoyi/web/controller/smtchs/  ← 业务控制器（新增）
│   └── src/main/resources/mapper/smtchs/              ← 业务 Mapper（新增）
└── sql/                 ← 数据库脚本
```

**无需手工合并框架代码**——直接 `mvn package` 即可。

### 4.2 配置数据源

编辑 `backend/ruoyi-admin/src/main/resources/application-druid.yml`：

```yaml
spring:
    datasource:
        druid:
            master:
                url: jdbc:mysql://localhost:3306/ruoyi_smtchs?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
                username: root
                password: 你的密码
```

### 4.3 配置 Redis

编辑 `backend/ruoyi-admin/src/main/resources/application.yml`：

```yaml
spring:
    redis:
        host: localhost
        port: 6379
        password:
        database: 0
```

### 4.4 配置上传路径

```yaml
ruoyi:
    profile: /home/ruoyi/uploadPath
```

### 4.5 构建与启动

```bash
cd backend
mvn clean package -DskipTests

# 启动
java -jar ruoyi-admin/target/ruoyi-admin-3.8.7.jar
```

启动成功后访问 `http://localhost:8080`，默认账号 `admin / admin123`。

### 4.6 验证业务接口

```bash
# 登录
curl -X POST http://localhost:8080/smtchs/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"zhangsan","password":"123456"}'

# 应返回 token 与 user 对象
```

---

## 五、员工端 H5 部署

### 5.1 配置 API 地址

创建 `.env.production`：

```bash
VITE_API_BASE_URL=/smtchs
```

或指向完整后端地址：

```bash
VITE_API_BASE_URL=http://your-server:8080/smtchs
```

### 5.2 构建

```bash
pnpm install
pnpm build
```

产物在 `dist/`。

### 5.3 部署

把 `dist/` 上传到 Nginx 静态目录：

```nginx
server {
    listen 80;
    server_name h5.your-domain.com;
    root /var/www/smtchs-h5;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # 代理后端 API
    location /smtchs/ {
        proxy_pass http://127.0.0.1:8080/smtchs/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

> 本项目用 hash 路由（URL 带 `#`），`try_files` 非必需但建议保留。

### 5.4 HTTPS（定位功能必需）

浏览器要求 GPS 定位必须在 HTTPS 下。用 Let's Encrypt 免费证书：

```bash
certbot --nginx -d h5.your-domain.com
```

---

## 六、管理端 PC 部署

### 6.1 获取若依前端

```bash
# 注意：frontend-admin/ 目录已存在（内含 src/api/smtchs 与 src/views/smtchs），
# 请克隆到临时目录后再合并，不要直接 clone 到该目录
git clone https://gitee.com/y_project/RuoYi-Vue3.git /tmp/ruoyi-frontend
cd /tmp/ruoyi-frontend && pnpm install
```

然后把本仓库的 `frontend-admin/src/` 覆盖到 `/tmp/ruoyi-frontend/src/`（只覆盖同名业务目录）。

### 6.2 合并业务代码

把本仓库 `frontend-admin/src/api/smtchs/` 和 `frontend-admin/src/views/smtchs/` 拷贝到若依前端对应目录。

### 6.3 配置后端地址

编辑 `frontend-admin/.env.production`：

```bash
VITE_APP_BASE_API = '/prod-api'
```

编辑 `frontend-admin/vite.config.js` 的 proxy：

```javascript
proxy: {
  '/prod-api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    rewrite: (p) => p.replace(/^\/prod-api/, '')
  }
}
```

### 6.4 构建

```bash
pnpm build:prod
```

产物在 `dist/`，部署方式同员工端。

---

## 七、排错清单

### 7.1 后端启动失败

| 现象 | 原因 | 解决 |
|---|---|---|
| `ClassNotFoundException: javax.servlet.*` | 用了 Spring Boot 3 | 降级到 2.5.15（见环境要求） |
| `Unknown database 'ruoyi_smtchs'` | 数据库未创建 | 执行 3.1 |
| `Table 'sys_user' doesn't exist` | 未导入若依基础表 | 执行 3.2 |
| `Redis connection refused` | Redis 未启动 | `redis-server` 或检查配置 |
| `Access denied for user 'root'` | 数据库密码错 | 检查 `application-druid.yml` |

### 7.2 接口 401

| 原因 | 解决 |
|---|---|
| 未带 token | 请求头加 `Authorization: Bearer <token>` |
| token 过期 | 重新登录 |
| Redis 中 token 丢失 | 检查 Redis 是否被清空 |

### 7.3 接口 403

| 原因 | 解决 |
|---|---|
| 角色无权限 | 在若依「角色管理」给角色分配菜单权限 |
| 菜单未导入 | 执行 `smtchs_menu.sql` |
| 权限标识拼写错 | 对照 `docs/API.md` 第十一节 |

### 7.4 前端页面空白

| 原因 | 解决 |
|---|---|
| `file://` 直接打开 | 用 HTTP 服务器（`python3 -m http.server`） |
| API 地址错 | 检查 `.env.production` 的 `VITE_API_BASE_URL` |
| 跨域 | Nginx 配置 proxy，或后端开 CORS |

### 7.5 地图不显示

| 原因 | 解决 |
|---|---|
| 无法访问高德瓦片 | 检查网络能否访问 `webrd0*.is.autonavi.com` |
| 记录无定位 | 正常降级，显示「未获取定位」占位 |
| HTTP 协议下定位被禁 | 部署 HTTPS |

### 7.6 照片上传失败

| 原因 | 解决 |
|---|---|
| 超过 300KB | 前端已压缩，检查压缩逻辑 |
| 格式不支持 | 只允许 jpg/jpeg/png/webp |
| 上传目录无权限 | `chmod 755 /home/ruoyi/uploadPath` |

### 7.7 Excel 导出乱码

| 原因 | 解决 |
|---|---|
| 响应头未设 UTF-8 | 检查 `SmtExcelExporter` 的 `setCharacterEncoding("utf-8")` |
| 文件名乱码 | 已用 `URLEncoder.encode` + `filename*=utf-8''` |

### 7.8 计费金额与预期不符

| 原因 | 解决 |
|---|---|
| 配置变更影响历史记录 | 不会——记录提交时已固化 `snap_*` 快照 |
| 取整规则理解偏差 | 见 `docs/BILLING-VERIFY.md` |
| 跨 0 点计算 | 结束时间 ≤ 开始时间视为次日，全部归入加班段 |

### 7.9 并发审核冲突

| 现象 | 说明 |
|---|---|
| `记录状态已变更，请刷新后重试` | 正常——乐观锁生效，两个管理员同时确认时第二个会失败 |

### 7.10 菜单重复导入报错

| 现象 | 解决 |
|---|---|
| `Duplicate entry` | `smtchs_menu.sql` 只能执行一次。先执行 `smtchs_rollback.sql` 的菜单清理段，再重新导入 |

---

## 八、生产环境检查清单

- [ ] 数据库已备份
- [ ] `application-druid.yml` 密码已改为强密码
- [ ] Redis 已设密码
- [ ] 上传目录已配置且有写权限
- [ ] HTTPS 证书已配置
- [ ] Nginx 已配置 gzip 压缩
- [ ] 日志目录已配置轮转
- [ ] 已关闭若依的 Swagger（生产环境）
- [ ] 已修改默认管理员密码
- [ ] 已配置定时备份（数据库 + 上传目录）
