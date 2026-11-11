# 工程师上门服务记录与提成核算系统

三端一体的上门服务工时计费系统：**员工端 H5**、**管理端移动**、**管理端 PC**，后端基于若依 RuoYi 3.9.2。

---

## 一、技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | 若依 RuoYi-Vue + Spring Boot | 3.9.2 / 4.1.2 |
| 后端 | MyBatis + Druid + Redis + JWT | — |
| 数据库 | MySQL / MariaDB | 8.0+ / 13.x |
| 员工端 H5 | Vue 3 + Vite + TypeScript | 3.5 / 6.4 / 5.7 |
| 管理端 PC | 若依 RuoYi-Vue3 + Element Plus | 3.9.2 / 2.13 |

---

## 二、目录结构

```
wh-wq-smtchs/
├── backend/                        # 后端（完整若依 3.9.2 源码）
│   ├── pom.xml                     # 父 POM（已注册 ruoyi-smtchs）
│   ├── ruoyi-common/               # 若依框架（原样）
│   ├── ruoyi-framework/            # 若依框架（原样）
│   ├── ruoyi-system/               # 若依框架（原样）
│   ├── ruoyi-quartz/               # 若依框架（原样）
│   ├── ruoyi-generator/            # 若依框架（原样）
│   ├── ruoyi-smtchs/               # 业务模块（本项目）
│   │   └── src/main/java/com/ruoyi/smtchs/
│   │       ├── billing/            # 计费引擎（纯函数 + 46 单测）
│   │       ├── domain/             # 7 个实体
│   │       ├── mapper/             # 6 个 Mapper 接口
│   │       ├── service/            # 7 接口 + 7 实现
│   │       ├── dto/                # 7 个 DTO/VO
│   │       └── util/               # 流水号生成器
│   ├── ruoyi-admin/                # 启动模块
│   │   └── src/main/
│   │       ├── java/com/ruoyi/web/controller/smtchs/   # 12 个控制器
│   │       └── resources/
│   │           ├── application*.yml
│   │           └── mapper/smtchs/                      # 6 个 Mapper XML
│   └── sql/                        # 建表 / 数据 / 菜单 / 回滚
│
├── src/                            # 员工端 H5 + 管理端移动（自研）
│   ├── api/                        # 10 个 HTTP 模块
│   ├── core/                       # 计费引擎 / 日期 / 坐标 / 图片
│   ├── data/service.ts             # 业务适配层（含缓存）
│   ├── views/employee/             # 员工端 5 页
│   ├── views/admin-mobile/         # 管理端移动 4 页
│   ├── components/                 # 8 个通用组件
│   └── styles/                     # 设计令牌与样式
│
├── frontend-admin/                 # 管理端 PC（完整若依 Vue3 源码）
│   └── src/
│       ├── api/smtchs/             # 7 个业务 API
│       └── views/smtchs/           # 8 个业务页面（含经营概览）
│
├── certs/                          # 局域网 HTTPS 自签证书
├── docs/                           # 文档
│   ├── API.md                      # 35 个接口 + 24 个权限
│   ├── DEPLOY.md                   # 部署与排错
│   ├── BILLING-VERIFY.md           # 前后端计费一致性
│   └── 局域网访问.md                # 局域网访问与证书
└── tests/                          # 前端单测（53 项）
```

---

## 三、快速开始

### 3.1 后端

```bash
cd backend
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

依赖：JDK 17、Maven 3.6+、MySQL 8+、Redis 5+

数据库初始化：

```bash
mysql -u root -p -e "CREATE DATABASE ruoyi_smtchs DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p ruoyi_smtchs < backend/sql/ry_*.sql          # 若依基础表
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_schema.sql # 业务表
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_data.sql   # 初始化数据
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_menu.sql   # 菜单权限
```

详见 `docs/DEPLOY.md`。

### 3.2 员工端 H5

```bash
pnpm install
pnpm dev        # https://localhost:5273
```

### 3.3 管理端 PC

```bash
cd frontend-admin
pnpm install
pnpm dev        # https://localhost:5280
```

---

## 四、演示账号

| 角色 | 账号 | 密码 | 入口 |
|---|---|---|---|
| 员工 | `zhangsan` / `lisi` / `wangwu` | `123456` | 员工端 H5 |
| 管理员 | `admin` | `admin123` | 管理端 PC / 移动 |

---

## 五、功能清单

### 员工端 H5（5 页）

- 首页：本月预估提成、按次/时长概览、服务地图、最近记录
- 新增记录：类型选择、GPS 定位、拍照压缩、金额实时预估
- 我的记录：按月分组、状态筛选、当日合计
- 记录详情：计费明细（分段说明）、现场凭证、流转记录
- 我的：账号信息、本月汇总、修改密码、退出登录

### 管理端移动（4 页）

- 金额汇总：提成总额、按员工/按类型汇总、下钻明细
- 详细记录：多条件筛选、列表查看
- 记录审核：Tab 切换、单条/批量确认、驳回、修正留痕
- 我的：账号卡、本月概览、修改密码、深色模式、退出登录

### 管理端 PC（8 页）

- 经营概览：主指标卡、提成排行、类型分布、最近提交
- 员工管理 / 工作类型 / 单价矩阵 / 计费规则
- 记录查询 / 记录审核 / 月度报表（双 Sheet Excel 导出）

---

## 六、核心设计

### 6.1 计费快照

记录提交时固化 5 个 `snap_*` 字段（计费模式、单价、加班起算、倍率、取整规则），
后续修改配置**不影响历史核算**。

### 6.2 前后端计费一致

两端各实现一份计费引擎，用 8,640 个用例交叉验证，**0 不一致**。
详见 `docs/BILLING-VERIFY.md`。

关键实现：金额用「先乘后除」+ 整数分运算，避免浮点误差。

### 6.3 并发审核

乐观锁：`UPDATE ... WHERE status = 'PENDING'`，检查影响行数，
两个管理员同时确认时第二个会失败并提示「记录状态已变更」。

### 6.4 修正留痕

已确认记录修正时保存 `before_json` / `after_json` 快照 + 操作人 + 时间，
详情页可查看完整变更链。

---

## 七、测试

```bash
# 后端（48 项：46 计费 + 2 Mapper 绑定）
cd backend && mvn test

# 前端（53 项：26 计费 + 27 验收）
pnpm test

# 类型检查
pnpm run typecheck
```

---

## 八、文档

| 文档 | 内容 |
|---|---|
| [docs/API.md](docs/API.md) | 35 个接口、24 个权限标识、请求响应示例 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 环境要求、数据库初始化、排错清单 |
| [docs/BILLING-VERIFY.md](docs/BILLING-VERIFY.md) | 前后端计费一致性验证报告 |
| [docs/局域网访问.md](docs/局域网访问.md) | 局域网访问、HTTPS 证书、常见问题 |
