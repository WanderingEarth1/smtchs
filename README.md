# 工程师上门服务记录与提成核算系统

一套面向**上门服务团队**的工时计费与提成核算系统。解决三个核心问题：

- **记录留痕**：工程师现场拍照 + GPS 定位，杜绝事后补单扯皮
- **审核把关**：管理员确认后锁定，防止随意改数
- **自动核算**：按次 / 按小时分别计费，加班按倍率，配置变更不影响历史

三端一体：**员工端 H5**、**管理端移动**、**管理端 PC**，后端基于若依 RuoYi 3.9.2。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [核心设计](#核心设计)
- [二次开发指引](#二次开发指引)
- [测试](#测试)
- [文档](#文档)
- [License](#license)

---

## 功能概览

### 员工端 H5

| 页面 | 功能 |
|---|---|
| 首页 | 本月预估提成、按次/时长概览、服务地图、最近记录 |
| 新增记录 | 类型选择、GPS 定位、拍照压缩、金额实时预估 |
| 我的记录 | 按月分组、状态筛选、当日合计 |
| 记录详情 | 计费明细（分段说明）、现场凭证、流转记录 |
| 我的 | 账号信息、本月汇总、修改密码、退出登录 |

### 管理端移动

| 页面 | 功能 |
|---|---|
| 金额汇总 | 提成总额、按员工/按类型汇总、下钻明细 |
| 详细记录 | 多条件筛选（月份/员工/关键词）、列表查看 |
| 记录审核 | Tab 切换、单条/批量确认、驳回、修正留痕 |
| 我的 | 账号卡、本月概览、修改密码、深色模式、退出登录 |

### 管理端 PC

| 页面 | 功能 |
|---|---|
| 经营概览 | 主指标卡、提成排行、类型分布、最近提交 |
| 员工管理 | 增删改查、重置密码、停用 |
| 工作类型 | 按次/按小时、启用停用 |
| 单价矩阵 | 员工 × 类型矩阵编辑、按行复制 |
| 计费规则 | 加班起算时间、倍率、取整规则 |
| 记录查询 | 多条件筛选、详情抽屉 |
| 记录审核 | Tab 切换、批量确认、驳回、修正 |
| 月度报表 | 汇总卡片、按员工/类型汇总、双 Sheet Excel 导出 |

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | 若依 RuoYi-Vue + Spring Boot | 3.9.2 / 4.1.2 |
| 后端 | MyBatis + Druid + Redis + JWT | — |
| 数据库 | MySQL / MariaDB | 8.0+ / 13.x |
| 员工端 H5 | Vue 3 + Vite + TypeScript | 3.5 / 6.4 / 5.7 |
| 管理端 PC | 若依 RuoYi-Vue3 + Element Plus | 3.9.2 / 2.13 |
| 地图 | Leaflet + 高德瓦片（GCJ-02） | 1.9 |
| Excel | Apache POI | 6.0 |

---

## 快速开始

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.6+ |
| MySQL | 8.0+ |
| Redis | 5.0+ |
| Node.js | 18+ |
| pnpm | 8+ |

### 1. 数据库初始化

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE ruoyi_smtchs DEFAULT CHARACTER SET utf8mb4"

# 导入若依基础表（backend/sql/ry_*.sql 是若依官方脚本）
mysql -u root -p ruoyi_smtchs < backend/sql/ry_20260417.sql

# 导入业务表与数据
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_schema.sql
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_data.sql
mysql -u root -p ruoyi_smtchs < backend/sql/smtchs_menu.sql
```

### 2. 后端

```bash
cd backend

# 修改数据库与 Redis 配置
vi ruoyi-admin/src/main/resources/application-druid.yml   # 数据库
vi ruoyi-admin/src/main/resources/application.yml         # Redis

# 构建与启动
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

启动后访问 `http://localhost:8080`。

### 3. 员工端 H5

```bash
pnpm install
pnpm dev
```

默认 `https://localhost:5273`（HTTPS 用于 GPS 定位，见下方说明）。

### 4. 管理端 PC

```bash
cd frontend-admin
pnpm install
pnpm dev
```

默认 `https://localhost:5280`。

### 5. 演示账号

| 角色 | 账号 | 密码 | 入口 |
|---|---|---|---|
| 员工 | `zhangsan` / `lisi` / `wangwu` | `123456` | 员工端 H5 |
| 管理员 | `admin` | `admin123` | 管理端 PC / 移动 |

> 首次登录前需把 `smt_employee.user_id` 关联到 `sys_user.user_id`，详见 `docs/DEPLOY.md`。

---

## 项目结构

```
wh-wq-smtchs/
├── backend/                        # 后端（完整若依 3.9.2 源码）
│   ├── ruoyi-common/               # 若依框架（原样）
│   ├── ruoyi-framework/            # 若依框架（原样）
│   ├── ruoyi-system/               # 若依框架（原样）
│   ├── ruoyi-quartz/               # 若依框架（原样）
│   ├── ruoyi-generator/            # 若依框架（原样）
│   ├── ruoyi-smtchs/               # ★ 业务模块
│   │   └── src/main/java/com/ruoyi/smtchs/
│   │       ├── billing/            # 计费引擎（纯函数 + 46 单测）
│   │       ├── domain/             # 7 个实体
│   │       ├── mapper/             # 6 个 Mapper 接口
│   │       ├── service/            # 7 接口 + 7 实现
│   │       ├── dto/                # 7 个 DTO/VO
│   │       └── util/               # 流水号生成器
│   ├── ruoyi-admin/                # 启动模块
│   │   └── src/main/
│   │       ├── java/com/ruoyi/web/controller/smtchs/   # ★ 12 个控制器
│   │       └── resources/mapper/smtchs/                # ★ 6 个 Mapper XML
│   └── sql/                        # 建表 / 数据 / 菜单 / 回滚
│
├── src/                            # ★ 员工端 H5 + 管理端移动（自研）
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
│       ├── api/smtchs/             # ★ 7 个业务 API
│       └── views/smtchs/           # ★ 8 个业务页面
│
├── docs/                           # 文档
└── tests/                          # 前端单测（53 项）
```

★ 标记的是本项目新增的业务代码，其余为若依官方源码。

---

## 核心设计

### 1. 计费快照（关键）

记录提交时固化 5 个字段：

| 字段 | 说明 |
|---|---|
| `snap_billing_mode` | 计费模式（按次/按小时） |
| `snap_unit_price` | 单价 |
| `snap_overtime_start` | 加班起算时间 |
| `snap_overtime_multiplier` | 加班倍率 |
| `snap_rounding_rule` | 取整规则 |

**为什么**：管理员改单价或计费规则后，历史记录的核算口径不能变。否则上月工资会因本月调价而变动。

### 2. 前后端计费一致

两端各实现一份计费引擎（前端用于实时预估，后端用于最终核算），用 **8,640 个用例**交叉验证，**0 不一致**。

关键实现：
- 金额用「先乘后除」+ 整数分运算，避免浮点误差
- 取整用整数运算 `((minutes + 29) / 30) * 30`，不用 `Math.ceil`

详见 `docs/BILLING-VERIFY.md`。

### 3. 并发审核

乐观锁：

```sql
UPDATE smt_work_record
SET status = 'APPROVED', confirmed_at = NOW()
WHERE id = ? AND status = 'PENDING'
```

检查影响行数，为 0 说明已被他人处理，返回「记录状态已变更，请刷新后重试」。

### 4. 修正留痕

已确认记录修正时保存：

| 字段 | 说明 |
|---|---|
| `before_json` | 修正前快照 |
| `after_json` | 修正后快照 |
| `operator_name` | 操作人 |
| `revised_at` | 修正时间 |
| `note` | 修正说明（必填） |

详情页可查看完整变更链。

### 5. GPS 定位与 HTTPS

浏览器规定 **GPS 只能在安全上下文（HTTPS 或 localhost）下使用**。

局域网 HTTP 访问时 `navigator.geolocation` 会报错：

```
Only secure origins are allowed
```

**解决方案**：项目内置自签证书生成脚本，两个前端在检测到 `certs/` 存在时自动启用 HTTPS。

```bash
cd certs
# 修改 san.cnf 里的 IP 列表为你的局域网 IP
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout key.pem -out cert.pem -days 365 -config san.cnf
```

---

## 二次开发指引

### 加一个新的计费模式

1. 后端 `billing/BillingMode.java` 加枚举值
2. 后端 `billing/BillingEngine.java` 加分支
3. 前端 `src/core/billing.ts` 同步加分支
4. 两端各加测试用例
5. 数据库 `smt_work_type.billing_mode` 字段是 VARCHAR，无需改表

### 加一个新的业务字段

1. `backend/sql/smtchs_schema.sql` 加列
2. `domain/SmtWorkRecord.java` 加字段 + getter/setter
3. `resources/mapper/smtchs/SmtWorkRecordMapper.xml` 的 resultMap 与 SQL 加映射
4. `src/core/types.ts` 加类型
5. `src/api/record.ts` 的 `normalizeRecord` 加转换
6. 前端页面加展示

### 换地图服务

当前用高德瓦片 + WGS84→GCJ02 坐标转换（`src/core/coord.ts`）。

换其他服务商：
- 天地图 / Mapbox：改 `src/components/MapView.vue` 的 `tileLayer` URL
- 若服务商用 WGS84（如 OpenStreetMap），去掉 `wgs84ToGcj02` 转换

### 加一个新的管理端页面

1. `frontend-admin/src/api/smtchs/` 加 API 模块
2. `frontend-admin/src/views/smtchs/<name>/index.vue` 加页面
3. 数据库 `sys_menu` 插入菜单记录（`component` 填 `smtchs/<name>/index`）
4. 给角色授权

> **注意**：菜单 `path` 不能与若依内置路由重名（如 `config` 会与 `system/config` 冲突导致 404）。建议加业务前缀，如 `billing-rule`。

---

## 测试

```bash
# 后端（48 项：46 计费 + 2 Mapper 绑定）
cd backend && mvn test

# 前端（53 项：26 计费 + 27 验收）
pnpm test

# 类型检查
pnpm run typecheck
```

### 已验证的业务闭环

| 闭环 | 内容 | 结果 |
|---|---|---|
| A | 员工新增工单 → 管理审核 → 工价结算 | ✅ |
| B | 管理驳回 → 员工看到原因 → 修改重提 → 再审核 | ✅ |
| C | 已确认工单修正 → 金额重算 + 留痕 | ✅ |
| D | 改单价 → 新工单用新价、旧工单不变 | ✅ |
| E | 改计费规则 → 新工单用新规则、旧工单不变 | ✅ |
| F | 跨 0 点 / 加班边界 / 取整规则 | ✅ 5/5 |

### 已验证的跨端联动

```
H5 员工新增工单 → PC 管理端审核 → PC 报表金额更新 → H5 员工看到新金额
```

实测：新增 ¥306.67 工单 → PC 审核 → 报表 ¥4,145.85 → H5 员工 ¥3,020.02，独立重算完全吻合。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/API.md](docs/API.md) | 35 个接口、24 个权限标识、请求响应示例 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 环境要求、数据库初始化、排错清单 |
| [docs/BILLING-VERIFY.md](docs/BILLING-VERIFY.md) | 前后端计费一致性验证报告 |

---

## License

[MIT](LICENSE)

本项目基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 与 [RuoYi-Vue3](https://gitee.com/y_project/RuoYi-Vue3) 构建，两者均为 MIT 协议。
