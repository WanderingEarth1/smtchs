# 全栈实现计划（若依后端 + Vue3 前端）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 Vue3 前端原型补齐若依（RuoYi-Vue3）后端，并把员工端 H5 与管理端移动端接到真实 API 上。

**Architecture:** 混合方案——员工端 H5 与管理端移动端保留现有 Vue3 原型（仅替换数据层为 HTTP）；管理端 PC 使用若依 Vue3 前端（Element Plus）；后端为若依 Spring Boot 3 单体应用，业务代码集中在新增的 `ruoyi-smtchs` 模块。计费引擎在服务端用纯 Java 重实现，与前端实现逐条结果一致。

**Tech Stack:** Spring Boot 3.x + MyBatis + MySQL 8 + Redis + JWT（后端）；Vue 3.5 + Vite 6 + TypeScript 5.7 + Element Plus（管理端 PC）；Vue 3.5 + Vite 6 + TypeScript 5.7（员工端 H5）

**Spec:** `docs/superpowers/specs/2026-11-10-fullstack-design.md`

## Global Constraints

- **若依版本**：RuoYi-Vue3（Spring Boot 3.x + Vue3 + Element Plus + Vite），非旧版 RuoYi-Vue（Vue2）
- **Java 版本**：JDK 17（若依 Spring Boot 3 要求）
- **MySQL**：8.0+，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`
- **金额类型**：一律 `BigDecimal`，禁止 `double` / `float`
- **金额精度**：`setScale(2, RoundingMode.HALF_UP)`
- **时长精度**：小时数保留 6 位小数（`setScale(6, RoundingMode.HALF_UP)`）
- **时间格式**：`HH:mm`（24 小时制，补零）；日期 `yyyy-MM-dd`；时间戳 `yyyy-MM-dd HH:mm:ss`
- **API 前缀**：`/smtchs`
- **响应格式**：`{ "code": 200, "msg": "操作成功", "data": {...} }`（若依 `AjaxResult` / `TableDataInfo`）
- **权限标识**：`smtchs:<模块>:<操作>`，如 `smtchs:employee:list`
- **表名前缀**：`smt_`
- **包名**：`com.ruoyi.smtchs`
- **照片上限**：单张 ≤300KB，每条记录 1~3 张
- **登录锁定**：连续失败 5 次锁定 15 分钟
- **会话有效期**：30 天
- **开发机无 Java/Maven/MySQL/Redis**：后端代码无法本地编译，需严格自检

## Review Focus

以下 5 类输入/条件最容易让使用者踩坑，每项都要有测试钉住：

1. **跨 0 点服务**（如 22:00~01:00）：结束时间小于开始时间时，必须按次日计算，且全部时长归入加班段。若按当日计算会得到负数时长。
2. **加班起算点边界**（恰好 18:00 开始或结束）：开始时间等于起算点时全部算加班；结束时间等于起算点时全部算正常。差一分钟结果就不同。
3. **取整规则的边界**（恰好 30 分钟 / 恰好 60 分钟）：`HALF_HOUR_UP` 对 30 分钟不应再补足，`HOUR_UP` 对 60 分钟不应再补足。`Math.ceil` 的浮点误差会多算半小时。
4. **金额分位精度**（如 1h40m × 85 元 × 2 倍）：必须用分钟数直接换算，不能先把小时数四舍五入到 4 位再乘，否则 283.33 会算成 283.34。
5. **并发审核同一条记录**：两个管理员同时确认，第二次必须失败而非重复计入提成。

---

## 文件结构

### 后端（新增）

```
backend/
├── pom.xml                                    # 父 POM
├── ruoyi-admin/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ruoyi/
│       │   ├── RuoYiApplication.java          # 若依自带
│       │   └── web/controller/smtchs/         # ★ 业务 Controller
│       │       ├── SmtAuthController.java
│       │       ├── SmtEmployeeController.java
│       │       ├── SmtWorkTypeController.java
│       │       ├── SmtPriceController.java
│       │       ├── SmtConfigController.java
│       │       ├── SmtRecordController.java
│       │       ├── SmtReviewController.java
│       │       ├── SmtReportController.java
│       │       └── SmtUploadController.java
│       └── resources/
│           ├── application.yml                # 若依自带，改数据源
│           └── mapper/smtchs/                 # ★ MyBatis XML
│               ├── SmtEmployeeMapper.xml
│               ├── SmtWorkTypeMapper.xml
│               ├── SmtPriceRuleMapper.xml
│               ├── SmtWorkRecordMapper.xml
│               ├── SmtRecordRevisionMapper.xml
│               └── SmtSysConfigMapper.xml
├── ruoyi-smtchs/                              # ★ 业务模块（新增）
│   ├── pom.xml
│   └── src/main/java/com/ruoyi/smtchs/
│       ├── billing/                           # 计费引擎（纯 Java，无 Spring）
│       │   ├── BillingEngine.java
│       │   ├── BillingInput.java
│       │   ├── BillingResult.java
│       │   ├── BillingMode.java
│       │   ├── RoundingRule.java
│       │   └── MonthlySummary.java
│       ├── domain/
│       │   ├── SmtEmployee.java
│       │   ├── SmtWorkType.java
│       │   ├── SmtPriceRule.java
│       │   ├── SmtWorkRecord.java
│       │   ├── SmtRecordPhoto.java
│       │   ├── SmtRecordRevision.java
│       │   └── SmtSysConfig.java
│       ├── mapper/
│       ├── service/
│       │   ├── ISmtEmployeeService.java
│       │   ├── ISmtWorkTypeService.java
│       │   ├── ISmtPriceService.java
│       │   ├── ISmtConfigService.java
│       │   ├── ISmtRecordService.java
│       │   ├── ISmtReviewService.java
│       │   └── ISmtReportService.java
│       │   └── impl/
│       ├── dto/                               # 请求/响应 DTO
│       └── util/
│           └── SerialNoGenerator.java
└── sql/
    ├── smtchs_schema.sql                      # 建表
    ├── smtchs_data.sql                        # 初始化数据
    └── smtchs_rollback.sql                    # 回滚
```

### 员工端 H5（改造）

```
frontend-employee/
├── src/
│   ├── api/                                   # ★ 新增
│   │   ├── request.ts                         # axios 封装
│   │   ├── auth.ts
│   │   ├── employee.ts
│   │   ├── worktype.ts
│   │   ├── price.ts
│   │   ├── config.ts
│   │   ├── record.ts
│   │   ├── review.ts
│   │   ├── report.ts
│   │   └── upload.ts
│   ├── data/
│   │   ├── service.ts                         # ★ 改造：内部改调 API
│   │   ├── storage.ts                         # ★ 删除
│   │   ├── seed.ts                            # ★ 删除（移到后端 SQL）
│   │   └── export.ts                          # ★ 删除（移到后端）
│   ├── core/                                  # 保留（billing.ts 保留用于前端预估）
│   ├── components/                            # 保留，零改动
│   ├── styles/                                # 保留，零改动
│   ├── layouts/                               # 保留
│   ├── views/                                 # ★ 改造数据获取
│   └── stores/app.ts                          # ★ 改造：token 管理
```

### 管理端 PC（若依前端）

```
frontend-admin/
└── src/views/smtchs/
    ├── employee/index.vue
    ├── worktype/index.vue
    ├── price/index.vue
    ├── config/index.vue
    ├── record/index.vue
    ├── review/index.vue
    └── report/index.vue
```

---

## Task 1: 数据库脚本

**Files:**
- Create: `backend/sql/smtchs_schema.sql`
- Create: `backend/sql/smtchs_data.sql`
- Create: `backend/sql/smtchs_rollback.sql`

**Interfaces:**
- Produces: 7 张表 `smt_employee` / `smt_work_type` / `smt_price_rule` / `smt_work_record` / `smt_record_photo` / `smt_record_revision` / `smt_sys_config`，供后续所有 Task 的 Mapper 使用

- [ ] **Step 1: 编写建表脚本 `smtchs_schema.sql`**

7 张表，字段严格按设计文档 5.3 节。关键约束：

```sql
-- 员工表：与若依 sys_user 一对一
CREATE TABLE smt_employee (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id         BIGINT       DEFAULT NULL COMMENT '关联 sys_user.user_id',
  name            VARCHAR(20)  NOT NULL COMMENT '姓名',
  account         VARCHAR(20)  NOT NULL COMMENT '登录账号',
  status          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
  failed_attempts INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  locked_until    DATETIME     DEFAULT NULL COMMENT '锁定截止时间',
  create_by       VARCHAR(64)  DEFAULT '',
  create_time     DATETIME     DEFAULT NULL,
  update_by       VARCHAR(64)  DEFAULT '',
  update_time     DATETIME     DEFAULT NULL,
  del_flag        CHAR(1)      NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_account (account),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案';

-- 服务记录表：计费参数以 snap_ 前缀冗余存储
CREATE TABLE smt_work_record (
  id                       BIGINT       NOT NULL AUTO_INCREMENT,
  serial_no                VARCHAR(32)  NOT NULL COMMENT '流水号',
  employee_id              BIGINT       NOT NULL,
  work_type_id             BIGINT       NOT NULL,
  service_date             DATE         NOT NULL,
  start_time               VARCHAR(5)   NOT NULL COMMENT 'HH:mm',
  end_time                 VARCHAR(5)   DEFAULT NULL COMMENT 'HH:mm，按次为空',
  location                 VARCHAR(60)  NOT NULL,
  latitude                 DECIMAL(10,6) DEFAULT NULL,
  longitude                DECIMAL(10,6) DEFAULT NULL,
  geo_captured             TINYINT(1)   NOT NULL DEFAULT 0,
  content                  VARCHAR(200) NOT NULL,
  remark                   VARCHAR(200) DEFAULT '',
  status                   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  is_backfill              TINYINT(1)   NOT NULL DEFAULT 0,
  reject_reason            VARCHAR(200) DEFAULT NULL,
  submitted_at             DATETIME     NOT NULL,
  confirmed_at             DATETIME     DEFAULT NULL,
  rejected_at              DATETIME     DEFAULT NULL,
  -- 计费快照
  snap_billing_mode        VARCHAR(16)  NOT NULL COMMENT 'BY_COUNT/BY_HOUR',
  snap_unit_price          DECIMAL(10,2) NOT NULL,
  snap_overtime_start      VARCHAR(5)   NOT NULL,
  snap_overtime_multiplier DECIMAL(4,2) NOT NULL,
  snap_rounding_rule       VARCHAR(16)  NOT NULL,
  create_by                VARCHAR(64)  DEFAULT '',
  create_time              DATETIME     DEFAULT NULL,
  update_by                VARCHAR(64)  DEFAULT '',
  update_time              DATETIME     DEFAULT NULL,
  del_flag                 CHAR(1)      NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_serial_no (serial_no),
  KEY idx_emp_date (employee_id, service_date),
  KEY idx_status (status),
  KEY idx_date (service_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务记录';
```

其余 5 张表按同样风格编写。

- [ ] **Step 2: 编写初始化数据 `smtchs_data.sql`**

插入：
- 1 个管理员（关联若依 `sys_user` 的 admin）
- 4 个员工（张三/李四/王五/赵六，赵六停用）
- 5 个工作类型（送货上门 BY_COUNT、安装调试 BY_HOUR、维修维护 BY_HOUR、巡检保养 BY_COUNT、客户培训 BY_HOUR 停用）
- 20 条单价规则（4 员工 × 5 类型）
- 1 条系统配置（18:00 / ×2 / EXACT）
- 18 条演示记录（覆盖三种状态、补录、跨 0 点、未定位）

- [ ] **Step 3: 编写回滚脚本 `smtchs_rollback.sql`**

```sql
DROP TABLE IF EXISTS smt_record_revision;
DROP TABLE IF EXISTS smt_record_photo;
DROP TABLE IF EXISTS smt_work_record;
DROP TABLE IF EXISTS smt_price_rule;
DROP TABLE IF EXISTS smt_work_type;
DROP TABLE IF EXISTS smt_employee;
DROP TABLE IF EXISTS smt_sys_config;
DELETE FROM sys_menu WHERE perms LIKE 'smtchs:%';
```

- [ ] **Step 4: 语法自检**

无 MySQL 环境，逐条核对：
- 每个 `CREATE TABLE` 有 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
- 所有 `COMMENT` 用单引号
- `DECIMAL` 精度足够（金额 10,2；经纬度 10,6；倍率 4,2）
- 外键不建（若依惯例用应用层保证）
- 索引覆盖高频查询（`idx_emp_date`、`idx_status`）

---

## Task 2: 计费引擎（纯 Java）

**Files:**
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/BillingMode.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/RoundingRule.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/BillingInput.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/BillingResult.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/MonthlySummary.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/billing/BillingEngine.java`
- Test: `backend/ruoyi-smtchs/src/test/java/com/ruoyi/smtchs/billing/BillingEngineTest.java`

**Interfaces:**
- Produces:
  - `BillingEngine.calculate(BillingInput) -> BillingResult`
  - `BillingEngine.applyRounding(int minutes, RoundingRule rule) -> int`
  - `BillingEngine.parseTimeToMinutes(String) -> int`
  - `BillingEngine.minutesToTime(int) -> String`
  - `BillingEngine.minutesText(int) -> String`
  - `BillingEngine.accumulate(MonthlySummary, BillingResult) -> MonthlySummary`
  - `BillingResult` 字段：`billingMode`, `count`, `normalHours`, `overtimeHours`, `normalAmount`, `overtimeAmount`, `total`, `rawNormalHours`, `rawOvertimeHours`, `crossMidnight`, `explanation`

- [ ] **Step 1: 编写枚举与 DTO**

`BillingMode`：`BY_COUNT`, `BY_HOUR`
`RoundingRule`：`EXACT`, `HALF_HOUR_UP`, `HOUR_UP`

`BillingInput` 用 Builder 模式，字段与前端 `BillingInput` 一一对应。
`BillingResult` 用不可变对象，`BigDecimal` 存金额，`BigDecimal` 存小时数（6 位小数）。

- [ ] **Step 2: 编写失败测试 `BillingEngineTest.java`**

移植前端 `tests/billing.test.ts` 的 26 个用例 + `tests/acceptance.test.ts` 的 20 条抽样，共 46 个测试方法。关键用例：

```java
@Test
@DisplayName("示例1：17:30~19:30，80元/小时，精确到分钟 → 280元")
void testExample1() {
    BillingResult r = BillingEngine.calculate(BillingInput.builder()
        .billingMode(BillingMode.BY_HOUR)
        .unitPrice(new BigDecimal("80"))
        .startTime("17:30").endTime("19:30")
        .overtimeStart("18:00").overtimeMultiplier(new BigDecimal("2"))
        .roundingRule(RoundingRule.EXACT)
        .build());
    assertEquals(new BigDecimal("40.00"), r.getNormalAmount());
    assertEquals(new BigDecimal("240.00"), r.getOvertimeAmount());
    assertEquals(new BigDecimal("280.00"), r.getTotal());
}

@Test
@DisplayName("跨0点：22:00~01:00 全部归入加班段")
void testCrossMidnight() {
    BillingResult r = BillingEngine.calculate(BillingInput.builder()
        .billingMode(BillingMode.BY_HOUR)
        .unitPrice(new BigDecimal("80"))
        .startTime("22:00").endTime("01:00")
        .overtimeStart("18:00").overtimeMultiplier(new BigDecimal("2"))
        .roundingRule(RoundingRule.EXACT)
        .build());
    assertTrue(r.isCrossMidnight());
    assertEquals(0, r.getNormalHours().compareTo(BigDecimal.ZERO));
    assertEquals(0, r.getOvertimeHours().compareTo(new BigDecimal("3")));
    assertEquals(new BigDecimal("480.00"), r.getTotal());
}

@Test
@DisplayName("分位精度：1h40m × 85元 × 2倍 = 283.33（不是 283.34）")
void testPrecision() {
    BillingResult r = BillingEngine.calculate(BillingInput.builder()
        .billingMode(BillingMode.BY_HOUR)
        .unitPrice(new BigDecimal("85"))
        .startTime("18:00").endTime("19:40")
        .overtimeStart("18:00").overtimeMultiplier(new BigDecimal("2"))
        .roundingRule(RoundingRule.EXACT)
        .build());
    assertEquals(new BigDecimal("283.33"), r.getOvertimeAmount());
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -pl ruoyi-smtchs test -Dtest=BillingEngineTest`
Expected: 编译失败（`BillingEngine` 不存在）

- [ ] **Step 4: 实现 `BillingEngine.java`**

逐行翻译前端 `src/core/billing.ts`。关键点：

```java
public static BillingResult calculate(BillingInput input) {
    if (input.getUnitPrice() == null || input.getUnitPrice().signum() < 0) {
        throw new IllegalArgumentException("单价必须为非负数");
    }

    // 6.1 按次
    if (input.getBillingMode() == BillingMode.BY_COUNT) {
        BigDecimal total = input.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
        return BillingResult.builder()
            .billingMode(BillingMode.BY_COUNT)
            .count(1)
            .normalHours(BigDecimal.ZERO)
            .overtimeHours(BigDecimal.ZERO)
            .normalAmount(total)
            .overtimeAmount(BigDecimal.ZERO)
            .total(total)
            .rawNormalHours(BigDecimal.ZERO)
            .rawOvertimeHours(BigDecimal.ZERO)
            .crossMidnight(false)
            .explanation(String.format("按次计费：1 次 × %s 元/次 = %s 元（按次不参与加班与取整）",
                input.getUnitPrice().stripTrailingZeros().toPlainString(),
                total.stripTrailingZeros().toPlainString()))
            .build();
    }

    // 6.2 按小时
    if (input.getEndTime() == null || input.getEndTime().isEmpty()) {
        throw new IllegalArgumentException("按小时计费必须填写结束时间");
    }

    int startMin = parseTimeToMinutes(input.getStartTime());
    int endMinRaw = parseTimeToMinutes(input.getEndTime());
    int otStartMin = parseTimeToMinutes(input.getOvertimeStart());

    boolean crossMidnight = endMinRaw <= startMin;
    int endMin = crossMidnight ? endMinRaw + 1440 : endMinRaw;

    int durationMin = endMin - startMin;
    boolean startsInOvertime = startMin >= otStartMin;
    int rawNormalMin = startsInOvertime ? 0 : Math.min(durationMin, otStartMin - startMin);
    int rawOvertimeMin = Math.max(0, durationMin - rawNormalMin);
    int overtimeStartMin = startMin + rawNormalMin;

    int normalMin = applyRounding(rawNormalMin, input.getRoundingRule());
    int overtimeMin = applyRounding(rawOvertimeMin, input.getRoundingRule());

    // 关键：直接用分钟数换算，不先转小时再乘
    BigDecimal normalAmount = BigDecimal.valueOf(normalMin)
        .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
        .multiply(input.getUnitPrice())
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal overtimeAmount = BigDecimal.valueOf(overtimeMin)
        .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
        .multiply(input.getUnitPrice())
        .multiply(input.getOvertimeMultiplier())
        .setScale(2, RoundingMode.HALF_UP);

    BigDecimal total = normalAmount.add(overtimeAmount).setScale(2, RoundingMode.HALF_UP);

    // ... 组装 explanation 与返回
}
```

`applyRounding` 用整数运算避免浮点误差：

```java
public static int applyRounding(int minutes, RoundingRule rule) {
    if (minutes <= 0) return 0;
    switch (rule) {
        case HALF_HOUR_UP:
            return ((minutes + 29) / 30) * 30;   // 整数向上取整
        case HOUR_UP:
            return ((minutes + 59) / 60) * 60;
        case EXACT:
        default:
            return minutes;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl ruoyi-smtchs test -Dtest=BillingEngineTest`
Expected: 46 个测试全部 PASS

- [ ] **Step 6: 与前端结果交叉验证**

在无 Java 环境时，人工核对：把前端 `pnpm test` 输出的 46 个用例期望值，与 Java 测试中的断言值逐条比对，确认完全一致。

---

## Task 3: 后端实体与 Mapper

**Files:**
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/domain/*.java`（7 个实体）
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/mapper/*.java`（6 个 Mapper 接口）
- Create: `backend/ruoyi-admin/src/main/resources/mapper/smtchs/*.xml`（6 个 XML）
- Create: `backend/ruoyi-smtchs/pom.xml`
- Modify: `backend/pom.xml`（父 POM 加模块）
- Modify: `backend/ruoyi-admin/pom.xml`（依赖 ruoyi-smtchs）

**Interfaces:**
- Consumes: Task 1 的表结构
- Produces: 实体类与 Mapper 接口，供 Task 4 的 Service 使用

- [ ] **Step 1: 编写 `ruoyi-smtchs/pom.xml`**

依赖 `ruoyi-common`，不依赖 `ruoyi-framework`（保持业务模块轻量）。

- [ ] **Step 2: 编写 7 个实体类**

继承若依 `BaseEntity`（提供 `createBy`/`createTime`/`updateBy`/`updateTime`/`params`）。
字段与 Task 1 表结构一一对应，用 `BigDecimal` 存金额与经纬度。

- [ ] **Step 3: 编写 6 个 Mapper 接口**

```java
public interface SmtWorkRecordMapper {
    List<SmtWorkRecord> selectRecordList(SmtWorkRecord query);
    SmtWorkRecord selectRecordById(Long id);
    int insertRecord(SmtWorkRecord record);
    int updateRecord(SmtWorkRecord record);
    int deleteRecordById(Long id);
    List<SmtWorkRecord> selectApprovedByMonth(@Param("month") String month, @Param("employeeId") Long employeeId);
    int countPending();
    String selectMaxSerialNo(@Param("prefix") String prefix);
}
```

- [ ] **Step 4: 编写 6 个 Mapper XML**

关键 SQL：

```xml
<!-- 按月查询已确认记录（报表用） -->
<select id="selectApprovedByMonth" resultMap="SmtWorkRecordResult">
    SELECT * FROM smt_work_record
    WHERE del_flag = '0'
      AND status = 'APPROVED'
      AND DATE_FORMAT(service_date, '%Y-%m') = #{month}
    <if test="employeeId != null">
      AND employee_id = #{employeeId}
    </if>
    ORDER BY service_date DESC, start_time DESC
</select>

<!-- 流水号：取当月最大序号 -->
<select id="selectMaxSerialNo" resultType="String">
    SELECT MAX(serial_no) FROM smt_work_record
    WHERE serial_no LIKE CONCAT(#{prefix}, '%')
</select>
```

- [ ] **Step 5: 语法自检**

- 所有 XML 的 `namespace` 与 Mapper 接口全限定名一致
- `resultMap` 的 `column` 与表字段一致（下划线转驼峰）
- `<if>` 条件不产生 `WHERE 1=1 AND` 之外的语法错误
- 无 `SELECT *` 在需要排除大字段的场景（照片表单独查）

---

## Task 4: 后端 Service 层

**Files:**
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/service/ISmt*Service.java`（7 个接口）
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/service/impl/Smt*ServiceImpl.java`（7 个实现）
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/dto/*.java`
- Create: `backend/ruoyi-smtchs/src/main/java/com/ruoyi/smtchs/util/SerialNoGenerator.java`

**Interfaces:**
- Consumes: Task 2 的 `BillingEngine`，Task 3 的 Mapper
- Produces: 7 个 Service 接口，供 Task 5 的 Controller 使用

- [ ] **Step 1: 编写 `SerialNoGenerator`**

格式 `SR{yyyyMM}{4位序号}`，如 `SR2026110019`。用 `synchronized` + 数据库 `MAX` 查询保证唯一。

- [ ] **Step 2: 编写 `SmtRecordServiceImpl`（最复杂）**

核心方法：

```java
@Transactional(rollbackFor = Exception.class)
public SmtWorkRecord createRecord(Long employeeId, RecordDraftDTO draft) {
    // 1. 校验草稿（字段必填、日期不超今天、照片 1~3 张）
    validateDraft(draft);

    // 2. 取工作类型与单价
    SmtWorkType workType = workTypeMapper.selectById(draft.getWorkTypeId());
    BigDecimal unitPrice = priceService.getUnitPrice(employeeId, draft.getWorkTypeId());
    if (unitPrice.signum() <= 0) {
        throw new ServiceException("该工作类型尚未配置单价，请联系管理员");
    }

    // 3. 取当前计费配置
    SmtSysConfig config = configService.getConfig();

    // 4. 组装记录（含计费快照）
    SmtWorkRecord record = new SmtWorkRecord();
    record.setSerialNo(serialNoGenerator.next());
    record.setEmployeeId(employeeId);
    // ... 其他字段
    record.setStatus("PENDING");
    record.setIsBackfill(!draft.getServiceDate().equals(LocalDate.now()));
    record.setSnapBillingMode(workType.getBillingMode());
    record.setSnapUnitPrice(unitPrice);
    record.setSnapOvertimeStart(config.getOvertimeStart());
    record.setSnapOvertimeMultiplier(config.getOvertimeMultiplier());
    record.setSnapRoundingRule(config.getRoundingRule());

    recordMapper.insertRecord(record);

    // 5. 保存照片
    savePhotos(record.getId(), draft.getPhotos());

    return record;
}
```

- [ ] **Step 3: 编写 `SmtReviewServiceImpl`（含并发控制）**

```java
@Transactional(rollbackFor = Exception.class)
public void approveRecord(Long recordId) {
    SmtWorkRecord record = recordMapper.selectRecordById(recordId);
    if (record == null) throw new ServiceException("记录不存在");
    if ("APPROVED".equals(record.getStatus())) throw new ServiceException("该记录已确认");
    if ("REJECTED".equals(record.getStatus())) throw new ServiceException("已驳回的记录需员工修改后重新提交");

    // 乐观锁：只有状态仍为 PENDING 才更新
    int affected = recordMapper.updateStatusIfPending(recordId, "APPROVED", new Date());
    if (affected == 0) {
        throw new ServiceException("记录状态已变更，请刷新后重试");
    }
}
```

对应 XML：

```xml
<update id="updateStatusIfPending">
    UPDATE smt_work_record
    SET status = #{status}, confirmed_at = #{confirmedAt}, update_time = NOW()
    WHERE id = #{id} AND status = 'PENDING' AND del_flag = '0'
</update>
```

- [ ] **Step 4: 编写 `SmtReportServiceImpl`**

```java
public MonthlyReportVO buildMonthlyReport(String month, Long employeeId, Long workTypeId) {
    List<SmtWorkRecord> records = recordMapper.selectApprovedByMonth(month, employeeId);

    MonthlySummary summary = MonthlySummary.empty();
    Map<Long, MonthlySummary> byEmployee = new LinkedHashMap<>();
    Map<Long, MonthlySummary> byWorkType = new LinkedHashMap<>();

    for (SmtWorkRecord r : records) {
        BillingResult result = BillingEngine.calculate(toBillingInput(r));
        BillingEngine.accumulate(summary, result);
        BillingEngine.accumulate(byEmployee.computeIfAbsent(r.getEmployeeId(), k -> MonthlySummary.empty()), result);
        BillingEngine.accumulate(byWorkType.computeIfAbsent(r.getWorkTypeId(), k -> MonthlySummary.empty()), result);
    }

    // 按金额降序排序
    // 统计待确认/已驳回条数
    // 组装 VO 返回
}
```

- [ ] **Step 5: 编写其余 5 个 Service**

员工管理、工作类型、单价矩阵、计费配置、认证（登录/锁定/改密）。

- [ ] **Step 6: 语法自检**

- 所有 `@Service` 类有 `@Service` 注解
- 所有 `@Transactional` 标注在写操作上
- 所有 Mapper 注入用 `@Autowired` 或构造器注入
- 无 `System.out.println`（用 `log.info`）
- 异常统一抛 `ServiceException`（若依自带）

---

## Task 5: 后端 Controller 层

**Files:**
- Create: `backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/smtchs/Smt*Controller.java`（9 个）

**Interfaces:**
- Consumes: Task 4 的 Service
- Produces: REST API，供 Task 6/7 的前端调用

- [ ] **Step 1: 编写 `SmtAuthController`**

```java
@RestController
@RequestMapping("/smtchs/auth")
public class SmtAuthController {

    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginDTO dto) {
        // 1. 查员工
        // 2. 检查停用
        // 3. 检查锁定
        // 4. 校验密码（BCrypt）
        // 5. 失败计数 / 锁定
        // 6. 生成 JWT
        // 7. 记录登录日志
        return AjaxResult.success(tokenVO);
    }

    @PostMapping("/logout")
    public AjaxResult logout() { ... }

    @GetMapping("/info")
    public AjaxResult info() { ... }

    @PutMapping("/password")
    public AjaxResult changePassword(@RequestBody ChangePasswordDTO dto) { ... }
}
```

- [ ] **Step 2: 编写 `SmtRecordController`**

```java
@RestController
@RequestMapping("/smtchs/record")
public class SmtRecordController {

    @PreAuthorize("@ss.hasPermi('smtchs:record:list')")
    @GetMapping("/my")
    public AjaxResult myRecords(@RequestParam String month,
                                @RequestParam(required = false) String status) { ... }

    @PreAuthorize("@ss.hasPermi('smtchs:record:add')")
    @PostMapping
    public AjaxResult add(@RequestBody RecordDraftDTO dto) { ... }

    @PreAuthorize("@ss.hasPermi('smtchs:record:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody RecordDraftDTO dto) { ... }

    @PreAuthorize("@ss.hasPermi('smtchs:record:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) { ... }
}
```

- [ ] **Step 3: 编写 `SmtReportController`（含 Excel 导出）**

```java
@PostMapping("/export")
public void export(HttpServletResponse response, @RequestParam String month) {
    List<SmtWorkRecord> records = reportService.listApproved(month);
    List<RecordDetailExcelVO> detailList = records.stream()
        .map(this::toExcelVO)
        .collect(Collectors.toList());
    List<EmployeeSummaryExcelVO> summaryList = reportService.buildEmployeeSummary(month);

    ExcelUtil<RecordDetailExcelVO> detailUtil = new ExcelUtil<>(RecordDetailExcelVO.class);
    // 双 Sheet 需用 EasyExcel 的 WriteSheet
    // 或分两次写：先写明细，再追加汇总
}
```

> 若依 `ExcelUtil` 单次只能导一个 Sheet。双 Sheet 需用 EasyExcel 原生 API，在 Task 5 中单独实现 `SmtExcelExporter` 工具类。

- [ ] **Step 4: 编写其余 6 个 Controller**

员工、工作类型、单价、配置、审核、上传。

- [ ] **Step 5: 编写权限菜单 SQL**

追加到 `smtchs_data.sql`：

```sql
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES ('服务记录管理', 0, 5, 'smtchs', NULL, 1, 'M', '0', '0', NULL, 'documentation', 'admin', NOW());
-- 子菜单：员工管理、工作类型、单价矩阵、计费规则、记录查询、审核、报表
-- 按钮权限：smtchs:employee:add / edit / remove / resetPwd 等
```

- [ ] **Step 6: 语法自检**

- 所有 Controller 有 `@RestController` + `@RequestMapping`
- 所有权限注解 `@PreAuthorize("@ss.hasPermi('...')")` 的权限标识与 SQL 中一致
- 所有 `@RequestBody` 参数有对应 DTO
- 返回类型统一 `AjaxResult` 或 `TableDataInfo`

---

## Task 6: 员工端 H5 数据层改造

**Files:**
- Create: `frontend-employee/src/api/request.ts`
- Create: `frontend-employee/src/api/*.ts`（9 个）
- Modify: `frontend-employee/src/data/service.ts`（1,126 行全部改造）
- Delete: `frontend-employee/src/data/storage.ts`
- Delete: `frontend-employee/src/data/seed.ts`
- Delete: `frontend-employee/src/data/export.ts`
- Modify: `frontend-employee/src/stores/app.ts`
- Modify: `frontend-employee/src/views/employee/*.vue`（5 个）
- Modify: `frontend-employee/src/views/admin-mobile/*.vue`（3 个）
- Modify: `frontend-employee/src/router/index.ts`

**Interfaces:**
- Consumes: Task 5 的 REST API
- Produces: 改造后的前端，功能与原型一致

- [ ] **Step 1: 编写 `api/request.ts`**

axios 封装：baseURL、token 注入、401 跳登录、错误提示。

- [ ] **Step 2: 编写 9 个 API 模块**

每个模块导出函数，返回 `Promise<T>`，类型复用 `src/core/types.ts`。

- [ ] **Step 3: 改造 `data/service.ts`**

保持函数名不变，签名从同步改异步：

```typescript
// 改造前
export function listMyRecords(employeeId: string, monthKey: string, status?: RecordStatus): WorkRecord[] {
  return loadRecords().filter(...)
}

// 改造后
export async function listMyRecords(monthKey: string, status?: RecordStatus): Promise<WorkRecord[]> {
  return recordApi.myRecords(monthKey, status)
}
```

> 注意：`employeeId` 参数移除，改由后端从 token 解析。

- [ ] **Step 4: 改造 `stores/app.ts`**

```typescript
const token = ref<string | null>(localStorage.getItem('smtchs.token'))
const currentUser = ref<Employee | null>(null)

async function login(account: string, password: string) {
  const res = await authApi.login(account, password)
  token.value = res.token
  localStorage.setItem('smtchs.token', res.token)
  currentUser.value = res.user
}

async function restore() {
  if (!token.value) return
  try {
    currentUser.value = await authApi.info()
  } catch {
    token.value = null
    localStorage.removeItem('smtchs.token')
  }
}
```

- [ ] **Step 5: 改造 8 个 Vue 页面的数据获取**

模式统一：

```vue
<!-- 改造前 -->
const records = computed(() => listMyRecords(user.id, monthKey.value))

<!-- 改造后 -->
const records = ref<WorkRecord[]>([])
const loading = ref(false)

async function loadRecords() {
  loading.value = true
  try {
    records.value = await listMyRecords(monthKey.value)
  } finally {
    loading.value = false
  }
}

watch(monthKey, loadRecords)
onMounted(loadRecords)
```

- [ ] **Step 6: 改造 `router/index.ts`**

守卫改为检查 token 而非 `currentUser`，并异步拉取用户信息。

- [ ] **Step 7: 运行测试**

Run: `cd frontend-employee && pnpm test`
Expected: 185 项测试中，`billing.test.ts`（26）与 `acceptance.test.ts`（27）应继续通过（纯逻辑未改）；其余 132 项依赖数据层的测试需改造或标记跳过。

> **决策点**：`service.test.ts`（47）、`requirements.test.ts`（74）、`export.test.ts`（11）依赖 localStorage 数据层，改造后无法直接运行。方案：把这些测试移到后端，用 JUnit 重写（Task 2 已覆盖计费部分）。前端保留 `billing.test.ts` + `acceptance.test.ts` 共 53 项。

- [ ] **Step 8: 手动验证**

启动前端 + 后端，走一遍完整流程：登录 → 新增记录 → 提交 → 管理员审核 → 报表 → 导出。

---

## Task 7: 管理端 PC（若依前端）

**Files:**
- Create: `frontend-admin/src/api/smtchs/*.js`（7 个）
- Create: `frontend-admin/src/views/smtchs/*/index.vue`（7 个）

**Interfaces:**
- Consumes: Task 5 的 REST API
- Produces: 管理端 PC 页面

- [ ] **Step 1: 搭建若依前端**

从 RuoYi-Vue3 官方仓库拉取前端代码，改 `vue.config.js` 的 proxy 指向后端。

- [ ] **Step 2: 编写 7 个 API 模块**

按若依惯例，`src/api/smtchs/employee.js`：

```javascript
import request from '@/utils/request'

export function listEmployee(query) {
  return request({ url: '/smtchs/employee/list', method: 'get', params: query })
}

export function addEmployee(data) {
  return request({ url: '/smtchs/employee', method: 'post', data })
}
// ...
```

- [ ] **Step 3: 编写 7 个页面**

每个页面用若依的 `<el-table>` + `<el-form>` + `<pagination>` 组件，参考现有 `src/views/admin/*.vue` 的字段与校验逻辑。

- [ ] **Step 4: 配置菜单与权限**

在若依「系统管理 → 菜单管理」中导入 Task 5 的菜单 SQL，或用代码生成器生成。

- [ ] **Step 5: 手动验证**

登录 → 各页面增删改查 → 审核 → 报表 → 导出 Excel。

---

## Task 8: 文档

**Files:**
- Create: `docs/API.md`
- Create: `docs/DEPLOY.md`
- Create: `docs/BILLING-VERIFY.md`

- [ ] **Step 1: 编写 `API.md`**

按设计文档 5.4 节的接口表，逐个补充请求参数、响应示例、错误码。

- [ ] **Step 2: 编写 `DEPLOY.md`**

包含：
- 环境要求（JDK 17、MySQL 8、Redis、Node 18+）
- 数据库初始化步骤
- 后端启动步骤（含 `application.yml` 配置项说明）
- 前端构建与部署
- Nginx 配置示例
- **排错清单**（10 个常见问题）

- [ ] **Step 3: 编写 `BILLING-VERIFY.md`**

记录前后端计费引擎的一致性验证：
- 46 个测试用例的期望值对照表
- 验证方法（前端 `pnpm test` + 后端 `mvn test`）
- 已知差异（无）

---

## 自检结果

### 1. 规格覆盖

| 设计文档章节 | 对应 Task |
|---|---|
| 5.1 整体结构 | Task 1-8 的文件结构 |
| 5.2 后端模块划分 | Task 2-5 |
| 5.3 数据库设计 | Task 1 |
| 5.4 API 设计 | Task 5 |
| 5.5 计费引擎 | Task 2 |
| 5.6 前端改造 | Task 6-7 |
| 六、实施顺序 | Task 1-8 顺序一致 |
| 九、交付物 | Task 8 |

### 2. 步骤扫描

每个 Step 都是单一动作，有明确产出。无「TBD」「适当处理」等空话。

### 3. 类型一致性

- `BillingResult` 字段在 Task 2 定义，Task 4 使用，名称一致
- `SmtWorkRecord` 字段在 Task 3 定义，Task 4/5 使用，名称一致
- API 路径在 Task 5 定义，Task 6/7 使用，路径一致

### 4. Review Focus 覆盖

| 风险项 | 覆盖 Task |
|---|---|
| 跨 0 点服务 | Task 2 Step 2（`testCrossMidnight`） |
| 加班起算点边界 | Task 2 Step 2（`testBoundaryStart` / `testBoundaryEnd`） |
| 取整规则边界 | Task 2 Step 2（`testRoundingBoundary`） |
| 金额分位精度 | Task 2 Step 2（`testPrecision`） |
| 并发审核 | Task 4 Step 3（`updateStatusIfPending` 乐观锁） |

### 5. 篇幅比例

计划 8 个 Task，代码块集中在关键算法与 SQL，未逐行转录实现。
