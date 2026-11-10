# API 接口文档

> 版本：V2.0
> 更新：2026-11-10
> 基础路径：`/smtchs`
> 认证方式：`Authorization: Bearer <token>`

---

## 一、通用约定

### 1.1 响应格式

所有接口统一返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { }
}
```

| code | 含义 |
|---|---|
| 200 | 成功 |
| 401 | 未登录或登录过期 |
| 403 | 无权限 |
| 500 | 业务异常（`msg` 为中文提示） |

### 1.2 分页响应

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [ ],
  "total": 100
}
```

### 1.3 时间格式

| 类型 | 格式 | 示例 |
|---|---|---|
| 日期 | `yyyy-MM-dd` | `2026-11-09` |
| 时间 | `HH:mm` | `17:30` |
| 时间戳 | `yyyy-MM-dd HH:mm:ss` | `2026-11-09 17:30:00` |
| 月份 | `yyyy-MM` | `2026-11` |

### 1.4 枚举值

| 枚举 | 取值 |
|---|---|
| 计费模式 | `BY_COUNT`（按次）/ `BY_HOUR`（按小时） |
| 记录状态 | `PENDING`（待确认）/ `APPROVED`（已确认）/ `REJECTED`（已驳回） |
| 取整规则 | `EXACT`（精确到分钟）/ `HALF_HOUR_UP`（半小时向上）/ `HOUR_UP`（整小时向上） |
| 员工状态 | `0`（启用）/ `1`（停用） |
| 角色 | `EMPLOYEE` / `ADMIN` |

---

## 二、认证 `/smtchs/auth`

### 2.1 登录

```
POST /smtchs/auth/login
```

**请求体**

```json
{
  "account": "zhangsan",
  "password": "123456"
}
```

**响应**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "token": "a1b2c3d4e5f6...",
    "user": {
      "id": 2,
      "name": "张三",
      "account": "zhangsan",
      "role": "EMPLOYEE"
    }
  }
}
```

**错误**

| 场景 | msg |
|---|---|
| 账号不存在 / 密码错误 | `账号或密码错误，还可尝试 4 次` |
| 连续错 5 次 | `密码连续输错 5 次，账号已锁定 15 分钟` |
| 账号停用 | `该账号已停用，请联系管理员` |

### 2.2 登出

```
POST /smtchs/auth/logout
```

### 2.3 当前用户信息

```
GET /smtchs/auth/info
```

**响应** `data` 为 `Employee` 对象（同登录响应的 `user`）。

### 2.4 修改本人密码

```
PUT /smtchs/auth/password
```

**请求体**

```json
{
  "oldPassword": "123456",
  "newPassword": "newpass123"
}
```

---

## 三、员工管理 `/smtchs/employee`

### 3.1 员工列表

```
GET /smtchs/employee/list?pageNum=1&pageSize=10&name=张&status=0
```

**权限** `smtchs:employee:list`

**响应** 分页格式，`rows` 为 `Employee[]`。

### 3.2 员工详情

```
GET /smtchs/employee/{id}
```

**权限** `smtchs:employee:query`

### 3.3 新增员工

```
POST /smtchs/employee
```

**权限** `smtchs:employee:add`

**请求体**

```json
{
  "name": "钱七",
  "account": "qianqi",
  "password": "pass123"
}
```

**错误**

| 场景 | msg |
|---|---|
| 账号重复 | `登录账号「qianqi」已存在` |
| 密码过短 | `初始密码至少 6 位` |
| 账号格式错 | `登录账号需为 3~20 位字母、数字或下划线` |

### 3.4 修改员工

```
PUT /smtchs/employee
```

**权限** `smtchs:employee:edit`

**请求体** `{ "id": 2, "name": "张三", "account": "zhangsan", "status": "0" }`

### 3.5 删除员工

```
DELETE /smtchs/employee/{id}
```

**权限** `smtchs:employee:remove`

**响应** `data: { "disabled": true }` — `true` 表示因有历史记录改为停用。

### 3.6 重置密码

```
PUT /smtchs/employee/{id}/password
```

**权限** `smtchs:employee:resetPwd`

**请求体** `{ "password": "newpass123" }`

---

## 四、工作类型 `/smtchs/worktype`

### 4.1 类型列表

```
GET /smtchs/worktype/list
```

**权限** `smtchs:worktype:list`

### 4.2 启用类型（员工端用）

```
GET /smtchs/worktype/enabled
```

无特殊权限，登录即可。

### 4.3 新增类型

```
POST /smtchs/worktype
```

**权限** `smtchs:worktype:add`

**请求体** `{ "name": "临时搬运", "billingMode": "BY_COUNT" }`

### 4.4 修改类型

```
PUT /smtchs/worktype
```

**权限** `smtchs:worktype:edit`

### 4.5 删除类型

```
DELETE /smtchs/worktype/{id}
```

**权限** `smtchs:worktype:remove`

**响应** `data: { "disabled": true }` — 被记录引用时改为停用。

---

## 五、单价矩阵 `/smtchs/price`

### 5.1 矩阵查询

```
GET /smtchs/price/matrix
```

**权限** `smtchs:price:list`

**响应**

```json
{
  "code": 200,
  "data": [
    { "id": 1, "employeeId": 2, "workTypeId": 1, "price": 50.00, "effectiveAt": "2026-10-01 00:00:00" }
  ]
}
```

### 5.2 设置单价

```
PUT /smtchs/price
```

**权限** `smtchs:price:edit`

**请求体** `{ "employeeId": 2, "workTypeId": 3, "price": 88.00 }`

### 5.3 按行复制

```
POST /smtchs/price/copy
```

**权限** `smtchs:price:edit`

**请求体** `{ "fromEmployeeId": 2, "toEmployeeIds": [3, 4] }`

**响应** `data: 10` — 复制的单价条数。

---

## 六、计费配置 `/smtchs/config`

### 6.1 读取配置

```
GET /smtchs/config
```

**响应**

```json
{
  "code": 200,
  "data": {
    "overtimeStart": "18:00",
    "overtimeMultiplier": 2.00,
    "roundingRule": "EXACT"
  }
}
```

### 6.2 修改配置

```
PUT /smtchs/config
```

**权限** `smtchs:config:edit`

**请求体** 同上。

**错误**

| 场景 | msg |
|---|---|
| 时间格式错 | `加班起算时间格式应为 HH:mm` |
| 倍率越界 | `加班倍率必须不小于 1` / `加班倍率不能超过 10` |

---

## 七、服务记录 `/smtchs/record`

### 7.1 我的记录

```
GET /smtchs/record/my?month=2026-11&status=PENDING
```

**响应** `data` 为 `WorkRecord[]`。

### 7.2 记录详情

```
GET /smtchs/record/{id}
```

### 7.3 新增记录

```
POST /smtchs/record
```

**权限** `smtchs:record:add`

**请求体**

```json
{
  "workTypeId": 3,
  "serviceDate": "2026-11-09",
  "startTime": "17:30",
  "endTime": "19:40",
  "location": "和平小区 2 栋 301",
  "latitude": 30.2741,
  "longitude": 120.1551,
  "geoCaptured": true,
  "content": "更换水龙头阀芯，测试无渗漏",
  "remark": "",
  "photoUrls": ["/profile/upload/2026/11/09/xxx.jpg"]
}
```

**错误**

| 场景 | msg |
|---|---|
| 未来日期 | `不能填写未来日期` |
| 缺照片 | `请至少拍摄 1 张现场凭证` |
| 超 3 张 | `最多上传 3 张照片` |
| 按小时缺结束时间 | `按小时计费需填写结束时间` |
| 未配单价 | `该工作类型尚未配置单价，请联系管理员` |

### 7.4 修改记录

```
PUT /smtchs/record?id={id}
```

**权限** `smtchs:record:edit`

**请求体** 同新增。

**错误** `已确认的记录已锁定，无法修改`

### 7.5 删除记录

```
DELETE /smtchs/record/{id}
```

**权限** `smtchs:record:remove`

### 7.6 全部记录（管理端）

```
GET /smtchs/record/list?pageNum=1&pageSize=10&month=2026-11&status=PENDING&employeeId=2
```

**权限** `smtchs:record:list`

**响应** 分页格式。

---

## 八、审核 `/smtchs/review`

### 8.1 确认单条

```
PUT /smtchs/review/approve/{id}
```

**权限** `smtchs:review:approve`

**错误**

| 场景 | msg |
|---|---|
| 已确认 | `该记录已确认` |
| 已驳回 | `已驳回的记录需员工修改后重新提交` |
| 并发冲突 | `记录状态已变更，请刷新后重试` |

### 8.2 批量确认

```
PUT /smtchs/review/approve-batch
```

**权限** `smtchs:review:approve`

**请求体** `[1, 2, 3]`

**响应** `data: { "success": 2, "failed": 1 }`

### 8.3 驳回

```
PUT /smtchs/review/reject/{id}
```

**权限** `smtchs:review:reject`

**请求体** `{ "reason": "照片不清晰，请重新拍摄" }`

**错误** `驳回时必须填写驳回原因`

### 8.4 修正已确认记录

```
PUT /smtchs/review/revise/{id}
```

**权限** `smtchs:review:revise`

**请求体**

```json
{
  "serviceDate": "2026-11-09",
  "startTime": "17:00",
  "endTime": "20:00",
  "location": "和平小区 2 栋 301",
  "content": "更换水龙头阀芯",
  "note": "电话核实实际结束时间为 20:00"
}
```

**错误**

| 场景 | msg |
|---|---|
| 未确认 | `仅已确认的记录需要走修正流程` |
| 缺说明 | `请填写修正说明` |
| 无变化 | `未检测到任何修改` |

### 8.5 修正痕迹

```
GET /smtchs/review/revisions/{id}
```

**响应**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "recordId": 5,
      "operatorName": "王管理",
      "revisedAt": "2026-11-09 12:52:00",
      "beforeJson": "{\"endTime\":\"19:40\"}",
      "afterJson": "{\"endTime\":\"20:00\"}",
      "note": "电话核实实际结束时间为 20:00"
    }
  ]
}
```

---

## 九、报表 `/smtchs/report`

### 9.1 月度报表

```
GET /smtchs/report/monthly?month=2026-11&employeeId=2&workTypeId=3
```

**权限** `smtchs:report:list`

**响应**

```json
{
  "code": 200,
  "data": {
    "monthKey": "2026-11",
    "summary": {
      "recordCount": 8,
      "countTotal": 3,
      "normalHoursTotal": 9.5,
      "overtimeHoursTotal": 4.5,
      "byCountAmount": 150.00,
      "byHourAmount": 1467.50,
      "totalAmount": 1617.50
    },
    "byEmployee": [
      { "employeeId": 2, "employeeName": "张三", "summary": { } }
    ],
    "byWorkType": [
      { "workTypeId": 3, "workTypeName": "维修维护", "billingMode": "BY_HOUR", "summary": { } }
    ],
    "approvedRecords": [ ],
    "pendingCount": 7,
    "rejectedCount": 1
  }
}
```

### 9.2 本人当月汇总

```
GET /smtchs/report/my-summary?month=2026-11
```

响应结构同 9.1（`byEmployee` 只含本人）。

### 9.3 导出 Excel

```
POST /smtchs/report/export?month=2026-11
```

**权限** `smtchs:report:export`

**响应** 二进制文件流，`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

**文件名** `月度报表_2026-11.xlsx`

**Sheet 结构**

| Sheet | 列数 | 列名 |
|---|---|---|
| 明细表 | 17 | 流水号、员工、日期、工作类型、计费模式、开始时间、结束时间、正常时长h、加班时长h、地点、定位、工作内容摘要、照片链接、状态、是否补录、单价、金额 |
| 按人汇总表 | 7 | 员工、次数合计、正常时长合计h、加班时长合计h、按次金额、按时金额、提成合计 |

---

## 十、上传 `/smtchs/upload`

### 10.1 上传照片

```
POST /smtchs/upload/photo
Content-Type: multipart/form-data
```

**表单字段** `file`

**限制**

| 项 | 限制 |
|---|---|
| 单张大小 | ≤ 300KB |
| 格式 | jpg / jpeg / png / webp |

**响应**

```json
{
  "code": 200,
  "msg": "上传成功",
  "data": {
    "url": "/profile/upload/2026/11/09/xxx.jpg",
    "size": 51200
  }
}
```

---

## 十一、权限标识清单

| 权限标识 | 说明 |
|---|---|
| `smtchs:employee:list` | 员工列表 |
| `smtchs:employee:query` | 员工详情 |
| `smtchs:employee:add` | 新增员工 |
| `smtchs:employee:edit` | 修改员工 |
| `smtchs:employee:remove` | 删除员工 |
| `smtchs:employee:resetPwd` | 重置密码 |
| `smtchs:worktype:list` | 类型列表 |
| `smtchs:worktype:add` | 新增类型 |
| `smtchs:worktype:edit` | 修改类型 |
| `smtchs:worktype:remove` | 删除类型 |
| `smtchs:price:list` | 单价矩阵 |
| `smtchs:price:edit` | 设置单价 |
| `smtchs:config:edit` | 修改计费配置 |
| `smtchs:record:list` | 全部记录 |
| `smtchs:record:add` | 新增记录 |
| `smtchs:record:edit` | 修改记录 |
| `smtchs:record:remove` | 删除记录 |
| `smtchs:review:approve` | 确认记录 |
| `smtchs:review:reject` | 驳回记录 |
| `smtchs:review:revise` | 修正记录 |
| `smtchs:report:list` | 查看报表 |
| `smtchs:report:export` | 导出报表 |
