# 前后端全栈实现设计文档

| 项 | 内容 |
|---|---|
| 日期 | 2026-11-10 |
| 状态 | 待批准 |
| 依据 | 《工程师上门服务记录与提成核算系统-需求文档》V1.0 + 现有前端原型 V1.1 |

---

## 一、意图与背景

现有工作区是一个**纯前端原型**（Vue 3 + Vite + TypeScript，约 20000 行，185 项测试全过），数据层用 localStorage 模拟后端。员工端 H5（科技蓝 + 地图 + 底部 FAB）与管理端 PC（若依风格布局）均已完整可用。

本次目标：**补齐真实后端，并把前端接到后端上**，形成可部署的全栈系统。

### 为什么是现在

原型已验证业务闭环（提交 → 审核 → 核算 → 导出），计费引擎有 20 条抽样人工核对背书。业务规则稳定，可以固化为服务端实现。

### 成功标准

1. 后端 API 覆盖需求文档全部功能编号（LOG/CFG/REC/AUD/STA）
2. 计费引擎在服务端重实现，与前端实现**逐条结果一致**（同一批测试用例）
3. 员工端 H5 保留现有设计，仅替换数据层为 HTTP 调用
4. 管理端 PC 使用若依 Vue3 前端，复用其权限体系
5. 前端 185 项测试继续通过（数据层替换后）

---

## 二、目标与非目标

### 目标

- 若依后端（Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis）完整业务实现
- 数据库表结构与初始化数据
- 员工端 H5 对接后端 API
- 管理端 PC 基于若依 Vue3 前端
- 文件上传（照片）落地到本地磁盘或对象存储

### 非目标（本期不做）

- 多租户、多管理员、权限分级（需求明确单管理员）
- 考勤 / 请假 / 排班
- 多级审批流
- 客户单位档案
- 员工端导出与代提
- 微信小程序 / 公众号对接
- 消息推送（短信/微信通知）

### 反目标（即使技术可行也算失败）

- 为了用若依而牺牲员工端移动体验（Element Plus 不适合手机端）
- 计费逻辑在前后端各写一套且结果不一致
- 把现有 185 项测试推倒重来

---

## 三、约束

### 环境约束（重要）

**本机没有 Java、Maven、MySQL、Redis、Docker。**

| 影响 | 应对 |
|---|---|
| 后端无法本地编译 | 代码需严格自检：依赖版本、包名、注解、SQL 语法 |
| 无法运行集成测试 | 计费引擎用**纯 Java 单元测试**（不依赖 Spring），可在无环境时人工审阅 |
| 无法验证数据库脚本 | SQL 需按 MySQL 8 语法逐条核对，提供可回滚脚本 |
| 无法端到端联调 | 提供完整的启动文档与排错清单 |

**验收方式调整**：后端交付为「可编译的完整代码 + 详细部署文档」，由接收方在有 Java 环境的机器上验证。前端改动可在本机验证。

### 技术约束

- 若依版本：**RuoYi-Vue3**（Spring Boot 3.x + Vue3 + Element Plus + Vite）
- 若依默认使用 MyBatis（XML 映射），本项目沿用，不引入 MyBatis-Plus 以免与若依脚手架冲突
- 若依自带 `sys_user` / `sys_role` 等权限表，业务表独立建，通过 `user_id` 关联

---

## 四、方案对比

### 方案 A：全部换成若依前端（未采纳）

管理端和员工端都用若依 Vue3 前端。

- ✅ 一套代码，权限体系统一
- ❌ Element Plus 的表格/表单在手机上体验差，员工端需大量覆盖样式
- ❌ 现有 20000 行前端与 185 项测试全部作废
- ❌ 科技蓝设计语言丢失

### 方案 B：全部保留现有前端，后端只做 API（未采纳）

- ✅ 前端零改动
- ❌ 管理端 PC 用不上若依的权限体系、字典管理、代码生成器
- ❌ 用户明确要求「后端用若依框架」，若依的价值一半在管理端前端

### 方案 C：混合（**已采纳**）

| 端 | 技术 | 理由 |
|---|---|---|
| 员工端 H5 | 保留现有 Vue3 原型，数据层改为 HTTP | 移动体验已打磨，Element Plus 不适合 |
| 管理端 PC | 若依 Vue3 前端（Element Plus） | 复用权限、字典、代码生成器 |
| 管理端移动 | 保留现有 Vue3 原型 | 同员工端理由 |
| 后端 | 若依 Spring Boot 3 | 用户指定 |

**取舍**：管理端 PC 的视觉会从「科技蓝定制」变成「若依默认蓝」，与员工端风格不完全统一。这是换取权限体系与开发效率的代价，已与用户确认。

---

## 五、架构设计

### 5.1 整体结构

```
wh-wq-smtchs/
├── backend/                    # 若依后端（Spring Boot 3）
│   ├── ruoyi-admin/            # 启动模块 + Controller
│   ├── ruoyi-system/           # 系统模块（若依自带）
│   ├── ruoyi-framework/        # 框架配置（若依自带）
│   ├── ruoyi-common/           # 公共工具（若依自带）
│   ├── ruoyi-smtchs/           # ★ 业务模块（新增）
│   └── sql/                    # 建表与初始化脚本
├── frontend-employee/          # 员工端 H5（现有原型迁移）
├── frontend-admin/             # 管理端 PC（若依 Vue3）
└── docs/                       # 设计文档
```

### 5.2 后端模块划分

`ruoyi-smtchs` 业务模块内部结构：

```
com.ruoyi.smtchs
├── domain/                     # 实体
│   ├── SmtEmployee.java
│   ├── SmtWorkType.java
│   ├── SmtPriceRule.java
│   ├── SmtWorkRecord.java
│   ├── SmtRecordRevision.java
│   └── SmtSysConfig.java
├── mapper/                     # MyBatis Mapper 接口
├── service/                    # 业务接口
│   └── impl/                   # 业务实现
├── controller/                 # REST 接口
│   ├── SmtAuthController.java      # 登录/登出/改密
│   ├── SmtEmployeeController.java  # 员工管理
│   ├── SmtWorkTypeController.java  # 工作类型
│   ├── SmtPriceController.java     # 单价矩阵
│   ├── SmtConfigController.java    # 计费规则
│   ├── SmtRecordController.java    # 服务记录
│   ├── SmtReviewController.java    # 审核
│   └── SmtReportController.java    # 报表与导出
├── billing/                    # ★ 计费引擎（纯 Java，无 Spring 依赖）
│   ├── BillingEngine.java
│   ├── BillingInput.java
│   ├── BillingResult.java
│   └── RoundingRule.java
└── util/                       # 工具（照片压缩、坐标等）
```

**关键设计**：`billing/` 包**不依赖 Spring**，是纯 Java 类。这样可以用 JUnit 直接测试，无需启动容器，也便于与前端实现逐条比对。

### 5.3 数据库设计

沿用需求文档第 7 章，按若依规范调整：

| 表名 | 说明 | 关键字段 |
|---|---|---|
| `smt_employee` | 员工（业务档案） | `id`, `user_id`(关联 sys_user), `name`, `account`, `status`, `failed_attempts`, `locked_until` |
| `smt_work_type` | 工作类型 | `id`, `name`, `billing_mode`, `enabled`, `sort` |
| `smt_price_rule` | 单价矩阵 | `id`, `employee_id`, `work_type_id`, `price`, `effective_at` |
| `smt_work_record` | 服务记录 | 见下 |
| `smt_record_revision` | 修正痕迹 | `id`, `record_id`, `operator_name`, `revised_at`, `before_json`, `after_json`, `note` |
| `smt_sys_config` | 计费配置 | `id`, `overtime_start`, `overtime_multiplier`, `rounding_rule` |
| `smt_record_photo` | 照片（独立表） | `id`, `record_id`, `url`, `sort`, `size` |

**`smt_work_record` 字段**：

```sql
id, serial_no, employee_id, work_type_id,
service_date, start_time, end_time,
location, latitude, longitude, geo_captured,
content, remark,
status, is_backfill, reject_reason,
submitted_at, confirmed_at, rejected_at,
-- 计费快照（提交时固化，保证历史口径不变）
snap_billing_mode, snap_unit_price, snap_overtime_start,
snap_overtime_multiplier, snap_rounding_rule,
create_by, create_time, update_by, update_time, del_flag
```

**设计要点**：计费参数以 `snap_*` 前缀冗余存储，而非 JOIN 查询当前配置。这保证配置变更不影响历史记录核算——与前端原型的 `billingSnapshot` 一致。

### 5.4 API 设计

统一前缀 `/smtchs`，遵循若依响应格式：

```json
{ "code": 200, "msg": "操作成功", "data": {...} }
```

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/smtchs/auth/login` | 登录 | 公开 |
| POST | `/smtchs/auth/logout` | 登出 | 登录 |
| GET | `/smtchs/auth/info` | 当前用户信息 | 登录 |
| PUT | `/smtchs/auth/password` | 修改本人密码 | 登录 |
| GET | `/smtchs/employee/list` | 员工列表 | admin |
| POST | `/smtchs/employee` | 新增员工 | admin |
| PUT | `/smtchs/employee` | 修改员工 | admin |
| DELETE | `/smtchs/employee/{id}` | 删除/停用 | admin |
| PUT | `/smtchs/employee/{id}/password` | 重置密码 | admin |
| GET | `/smtchs/worktype/list` | 工作类型列表 | 登录 |
| POST/PUT/DELETE | `/smtchs/worktype` | 增删改 | admin |
| GET | `/smtchs/price/matrix` | 单价矩阵 | admin |
| PUT | `/smtchs/price` | 设置单价 | admin |
| POST | `/smtchs/price/copy` | 按行复制 | admin |
| GET | `/smtchs/config` | 读取计费规则 | 登录 |
| PUT | `/smtchs/config` | 修改计费规则 | admin |
| GET | `/smtchs/record/my` | 我的记录（按月+状态） | 登录 |
| GET | `/smtchs/record/{id}` | 记录详情 | 登录 |
| POST | `/smtchs/record` | 新增记录 | employee |
| PUT | `/smtchs/record` | 修改记录 | employee |
| DELETE | `/smtchs/record/{id}` | 删除记录 | employee |
| GET | `/smtchs/record/list` | 全部记录（筛选） | admin |
| PUT | `/smtchs/review/approve/{id}` | 确认单条 | admin |
| PUT | `/smtchs/review/approve-batch` | 批量确认 | admin |
| PUT | `/smtchs/review/reject/{id}` | 驳回 | admin |
| PUT | `/smtchs/review/revise/{id}` | 修正已确认 | admin |
| GET | `/smtchs/review/revisions/{id}` | 修正痕迹 | 登录 |
| GET | `/smtchs/report/monthly` | 月度报表 | admin |
| GET | `/smtchs/report/my-summary` | 本人当月汇总 | employee |
| GET | `/smtchs/report/export` | 导出 Excel | admin |
| POST | `/smtchs/upload/photo` | 上传照片 | 登录 |

### 5.5 计费引擎（服务端）

**必须与前端实现逐条一致**。实现策略：

1. 把前端 `src/core/billing.ts` 的逻辑逐行翻译为 Java
2. 把前端 `tests/billing.test.ts` 的 26 个用例翻译为 JUnit 测试
3. 把 `tests/acceptance.test.ts` 的 20 条抽样用例也翻译过去
4. 两边跑同一批用例，结果必须完全相同

关键精度处理（前端已踩过的坑）：

```java
// 正确：直接用分钟数换算
BigDecimal amount = BigDecimal.valueOf(minutes)
    .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
    .multiply(unitPrice)
    .setScale(2, RoundingMode.HALF_UP);

// 错误：先把小时数四舍五入到 4 位再乘，会引入分位误差
```

金额一律用 `BigDecimal`，禁止 `double`。

### 5.6 前端改造

#### 员工端 H5（保留设计，换数据层）

现有 `src/data/service.ts` 是同步函数（读 localStorage）。改为 HTTP 后变成异步，影响面：

| 现状 | 改造后 |
|---|---|
| `listMyRecords()` 同步返回数组 | `await listMyRecords()` 返回 Promise |
| 组件里 `computed(() => listMyRecords(...))` | 改为 `ref` + `onMounted` 加载，或用 `useAsyncData` |
| `dataVersion` 自增触发重算 | 改为请求后刷新 |

**改造范围**：约 15 个 Vue 文件的数据获取部分。UI 与样式零改动。

新增 `src/api/` 目录，封装 HTTP 调用；`src/data/service.ts` 保留为**适配层**，内部改为调 API，对外签名尽量不变（同步改异步）。

#### 管理端 PC（若依 Vue3）

直接使用若依前端脚手架，新增业务页面：

```
src/views/smtchs/
├── employee/index.vue       # 员工管理
├── worktype/index.vue       # 工作类型
├── price/index.vue          # 单价矩阵
├── config/index.vue         # 计费规则
├── record/index.vue         # 记录查询
├── review/index.vue         # 审核
└── report/index.vue         # 报表与导出
```

权限标识：`smtchs:employee:list` / `smtchs:review:approve` 等，写入若依菜单表。

---

## 六、实施顺序

分 6 个阶段，每阶段可独立验收：

| 阶段 | 内容 | 可验证性 |
|---|---|---|
| 1 | 数据库脚本 + 计费引擎（纯 Java）+ JUnit 测试 | ✅ 计费引擎可独立测试 |
| 2 | 若依后端骨架 + 实体/Mapper/Service | ⚠️ 需 Java 环境编译 |
| 3 | Controller + API 文档 | ⚠️ 需环境 |
| 4 | 员工端 H5 数据层改造 | ✅ 本机可验证 |
| 5 | 管理端 PC 若依前端页面 | ⚠️ 需环境 |
| 6 | 部署文档 + 联调清单 | — |

---

## 七、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 无 Java 环境，后端无法编译验证 | 交付代码可能有编译错误 | 严格自检 + 提供逐行审阅清单 + 依赖版本锁定 |
| 若依版本差异导致代码不兼容 | 需返工 | 锁定 RuoYi-Vue3 具体版本，按该版本包结构组织 |
| 前后端计费结果不一致 | 金额纠纷 | 同一批测试用例双向验证 |
| 前端异步化改造引入 bug | 功能回归 | 保留 185 项测试，改造后必须全过 |
| 照片存储方案未定 | 部署受阻 | 默认本地磁盘，配置项可切 OSS |

---

## 八、待确认项

以下为我的判断，请确认或纠正：

1. **若依版本**：使用 RuoYi-Vue3（Spring Boot 3 + Vue3 + Element Plus），而非旧版 RuoYi-Vue（Spring Boot 2 + Vue2）
2. **照片存储**：默认本地磁盘 `profile/upload/`，通过配置项可切换 OSS
3. **管理端 PC 视觉**：接受若依默认蓝色主题，不做科技蓝定制
4. **员工端与管理端部署**：两个独立前端工程，分别部署；后端同一套 API
5. **Excel 导出**：服务端用 EasyExcel（若依自带）生成，替代前端 SheetJS
6. **数据迁移**：不做 localStorage 到数据库的迁移，后端从空库 + 初始化演示数据开始

---

## 九、交付物

| 交付物 | 说明 |
|---|---|
| `backend/` | 若依后端完整代码（含业务模块） |
| `backend/sql/` | 建表脚本 + 初始化数据 + 回滚脚本 |
| `frontend-employee/` | 员工端 H5（改造后） |
| `frontend-admin/` | 管理端 PC（若依 Vue3） |
| `docs/API.md` | 接口文档 |
| `docs/DEPLOY.md` | 部署文档（含排错清单） |
| `docs/BILLING-VERIFY.md` | 前后端计费一致性验证报告 |
