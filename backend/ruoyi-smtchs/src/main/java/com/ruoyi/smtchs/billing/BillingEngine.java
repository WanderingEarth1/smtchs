package com.ruoyi.smtchs.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计费引擎 —— 严格对应需求文档第 6 章「计费规则详细说明」。
 *
 * <p>6.1 按次计费：金额 = 单价（元/次） × 次数（一条记录 = 1 次），不参与加班与取整。</p>
 * <p>6.2 按小时计费（含加班）：</p>
 * <ol>
 *   <li>分段：以加班起算时间为界切分
 *       T1 = min(结束, 起算) - 开始（开始已晚于起算点则 T1 = 0）
 *       T2 = 结束 - max(开始, 起算)（结束早于起算点则 T2 = 0；跨 0 点连续服务归入加班段）</li>
 *   <li>取整：T1、T2 分别按当前取整规则向上补足（EXACT 不取整）</li>
 *   <li>计费：金额 = T1 × 单价 + T2 × 单价 × 加班倍率</li>
 * </ol>
 *
 * <p>全部金额使用 {@link BigDecimal} 计算，禁止 double 参与金额运算。</p>
 */
public final class BillingEngine {

    /** 一天的分钟数 */
    private static final int MINUTES_PER_DAY = 1440;

    /** 一小时的分钟数 */
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /** 分钟 → 小时 换算时的中间精度（先算到 10 位小数，再乘单价，避免分位误差） */
    private static final int RATE_SCALE = 10;

    /** HH:mm 格式校验 */
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}$");

    private BillingEngine() {
    }

    /**
     * 计算单条记录的提成金额（对应 TS 的 calculateBilling）。
     *
     * @param input 计费入参
     * @return 计费结果
     */
    public static BillingResult calculate(BillingInput input) {
        if (input == null) {
            throw new IllegalArgumentException("计费入参不能为空");
        }

        BillingMode billingMode = input.getBillingMode();
        BigDecimal unitPrice = input.getUnitPrice();

        if (billingMode == null) {
            throw new IllegalArgumentException("计费方式不能为空");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("单价必须为非负数");
        }

        // ---------- 6.1 按次计费 ----------
        if (billingMode == BillingMode.BY_COUNT) {
            BigDecimal total = roundMoney(unitPrice);
            return BillingResult.builder()
                    .billingMode(billingMode)
                    .count(1)
                    .normalHours(BigDecimal.ZERO)
                    .overtimeHours(BigDecimal.ZERO)
                    .normalAmount(total)
                    .overtimeAmount(BigDecimal.ZERO)
                    .total(total)
                    .rawNormalHours(BigDecimal.ZERO)
                    .rawOvertimeHours(BigDecimal.ZERO)
                    .crossMidnight(false)
                    .explanation("按次计费：1 次 × " + formatNumber(unitPrice)
                            + " 元/次 = " + formatNumber(total) + " 元（按次不参与加班与取整）")
                    .build();
        }

        // ---------- 6.2 按小时计费 ----------
        String endTime = input.getEndTime();
        if (endTime == null || endTime.trim().isEmpty()) {
            throw new IllegalArgumentException("按小时计费必须填写结束时间");
        }

        int startMin = parseTimeToMinutes(input.getStartTime());
        int endMinRaw = parseTimeToMinutes(endTime);
        int otStartMin = parseTimeToMinutes(input.getOvertimeStart());

        // 跨 0 点：结束时间 <= 开始时间视为次日结束（连续服务）
        boolean crossMidnight = endMinRaw <= startMin;
        int endMin = crossMidnight ? endMinRaw + MINUTES_PER_DAY : endMinRaw;

        if (endMin <= startMin) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }

        // 1) 分段（严格按文档 6.2 步骤 1）
        //    T1 = min(结束, 起算) - 开始；若开始已晚于起算点，T1 = 0
        //    T2 = 结束 - max(开始, 起算)；若结束早于起算点，T2 = 0
        //    跨 0 点的连续服务时长归入加班时段连续计算
        int durationMin = endMin - startMin;
        boolean startsInOvertime = startMin >= otStartMin;
        int rawNormalMin = startsInOvertime ? 0 : Math.min(durationMin, otStartMin - startMin);
        int rawOvertimeMin = Math.max(0, durationMin - rawNormalMin);
        // 加班段在「当日分钟轴」上的起点（用于展示）
        int overtimeStartMin = startMin + rawNormalMin;

        // 2) 取整（T1、T2 分别向上补足）
        RoundingRule roundingRule = input.getRoundingRule() == null
                ? RoundingRule.EXACT : input.getRoundingRule();
        int normalMin = applyRounding(rawNormalMin, roundingRule);
        int overtimeMin = applyRounding(rawOvertimeMin, roundingRule);

        // 3) 计费（直接用分钟数换算，避免小时数四舍五入引入的分位误差）
        BigDecimal normalHours = toHours(normalMin);
        BigDecimal overtimeHours = toHours(overtimeMin);

        BigDecimal normalAmount = BigDecimal.valueOf(normalMin)
                .divide(MINUTES_PER_HOUR, RATE_SCALE, RoundingMode.HALF_UP)
                .multiply(unitPrice)
                .setScale(BillingResult.MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal overtimeMultiplier = input.getOvertimeMultiplier() == null
                ? BigDecimal.ONE : input.getOvertimeMultiplier();
        BigDecimal overtimeAmount = BigDecimal.valueOf(overtimeMin)
                .divide(MINUTES_PER_HOUR, RATE_SCALE, RoundingMode.HALF_UP)
                .multiply(unitPrice)
                .multiply(overtimeMultiplier)
                .setScale(BillingResult.MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal total = normalAmount
                .add(overtimeAmount)
                .setScale(BillingResult.MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal rawNormalHours = toHours(rawNormalMin);
        BigDecimal rawOvertimeHours = toHours(rawOvertimeMin);

        List<String> parts = new ArrayList<>();
        parts.add(
                "正常段 " + input.getStartTime() + "~" + minutesToTime(startMin + rawNormalMin)
                        + "：原始 " + minutesText(rawNormalMin)
                        + (roundingRule == RoundingRule.EXACT ? "" : " → 取整 " + formatNumber(normalHours) + "h")
                        + " × " + formatNumber(unitPrice) + " = " + formatNumber(normalAmount) + " 元");

        if (rawOvertimeMin > 0) {
            parts.add(
                    "加班段 " + minutesToTime(overtimeStartMin) + "~" + minutesToTime(endMin)
                            + "：原始 " + minutesText(rawOvertimeMin)
                            + (roundingRule == RoundingRule.EXACT ? "" : " → 取整 " + formatNumber(overtimeHours) + "h")
                            + " × " + formatNumber(unitPrice)
                            + " × " + formatNumber(overtimeMultiplier)
                            + " = " + formatNumber(overtimeAmount) + " 元");
        } else {
            parts.add("无加班时段");
        }
        parts.add("合计 " + formatNumber(total) + " 元");

        return BillingResult.builder()
                .billingMode(billingMode)
                .count(0)
                .normalHours(normalHours)
                .overtimeHours(overtimeHours)
                .normalAmount(normalAmount)
                .overtimeAmount(overtimeAmount)
                .total(total)
                .rawNormalHours(rawNormalHours)
                .rawOvertimeHours(rawOvertimeHours)
                .crossMidnight(crossMidnight)
                .explanation(String.join("；", parts))
                .build();
    }

    /**
     * 按取整规则向上补足时长（单位：分钟）。
     * <ul>
     *   <li>EXACT：精确到分钟，不取整</li>
     *   <li>HALF_HOUR_UP：半小时向上取整</li>
     *   <li>HOUR_UP：整小时向上取整</li>
     * </ul>
     * 使用整数运算，避免 {@code Math.ceil(double)} 的浮点误差。
     */
    public static int applyRounding(int minutes, RoundingRule rule) {
        if (minutes <= 0) {
            return 0;
        }
        if (rule == null) {
            return minutes;
        }
        switch (rule) {
            case EXACT:
                return minutes;
            case HALF_HOUR_UP:
                return ((minutes + 29) / 30) * 30;
            case HOUR_UP:
                return ((minutes + 59) / 60) * 60;
            default:
                return minutes;
        }
    }

    /**
     * 把 HH:mm 解析为「当日分钟数」。
     *
     * @param time HH:mm 格式时间
     * @return 当日分钟数
     */
    public static int parseTimeToMinutes(String time) {
        String text = time == null ? "" : time.trim();
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("时间格式非法：" + time + "（应为 HH:mm）");
        }
        int hour = Integer.parseInt(text.substring(0, text.indexOf(':')));
        int minute = Integer.parseInt(text.substring(text.indexOf(':') + 1));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("时间超出范围：" + time);
        }
        return hour * 60 + minute;
    }

    /**
     * 分钟数格式化为 HH:mm（跨日自动回绕）。
     */
    public static String minutesToTime(int totalMinutes) {
        int normalized = ((totalMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        int hour = normalized / 60;
        int minute = normalized % 60;
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /**
     * 分钟 → 可读时长（1h40m / 30min / 0h），用于计算说明文本。
     */
    public static String minutesText(int minutes) {
        if (minutes <= 0) {
            return "0h";
        }
        int hour = minutes / 60;
        int minute = minutes % 60;
        if (hour == 0) {
            return minute + "min";
        }
        if (minute == 0) {
            return hour + "h";
        }
        return hour + "h" + minute + "m";
    }

    /**
     * 金额四舍五入到分。
     */
    public static BigDecimal roundMoney(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(BillingResult.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 把一条核算结果累加进汇总（对应 TS 的 accumulate）。
     *
     * @param summary 汇总对象（原地修改）
     * @param result  单条核算结果
     * @return 同一个汇总对象
     */
    public static MonthlySummary accumulate(MonthlySummary summary, BillingResult result) {
        if (summary == null) {
            throw new IllegalArgumentException("汇总对象不能为空");
        }
        if (result == null) {
            throw new IllegalArgumentException("核算结果不能为空");
        }

        summary.setRecordCount(summary.getRecordCount() + 1);
        summary.setCountTotal(summary.getCountTotal() + result.getCount());
        summary.setNormalHoursTotal(tidy(nz(summary.getNormalHoursTotal()).add(nz(result.getNormalHours()))));
        summary.setOvertimeHoursTotal(tidy(nz(summary.getOvertimeHoursTotal()).add(nz(result.getOvertimeHours()))));

        if (result.getBillingMode() == BillingMode.BY_COUNT) {
            summary.setByCountAmount(roundMoney(nz(summary.getByCountAmount()).add(nz(result.getTotal()))));
        } else {
            summary.setByHourAmount(roundMoney(nz(summary.getByHourAmount()).add(nz(result.getTotal()))));
        }
        summary.setTotalAmount(roundMoney(nz(summary.getByCountAmount()).add(nz(summary.getByHourAmount()))));

        return summary;
    }

    /**
     * 分钟 → 小时（保留 6 位小数）。
     */
    private static BigDecimal toHours(int minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(MINUTES_PER_HOUR, BillingResult.HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 清除浮点噪声：保留 6 位小数（对应 TS 的 tidy）。
     */
    private static BigDecimal tidy(BigDecimal value) {
        return nz(value).setScale(BillingResult.HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 空值兜底为 0。
     */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 按 JS 数字的字面量习惯输出：去掉无意义的尾随零（40.00 → 40，0.500000 → 0.5）。
     */
    private static String formatNumber(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
