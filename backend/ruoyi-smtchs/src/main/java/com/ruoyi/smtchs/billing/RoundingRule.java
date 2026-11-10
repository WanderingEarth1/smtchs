package com.ruoyi.smtchs.billing;

/**
 * 时长取整规则（对应 TS 源码 types.ts 中的 RoundingRule）。
 */
public enum RoundingRule {
    EXACT,          // 精确到分钟
    HALF_HOUR_UP,   // 半小时向上取整
    HOUR_UP         // 整小时向上取整
}
