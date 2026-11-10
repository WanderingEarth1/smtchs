package com.ruoyi.smtchs.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BillingEngine} 单元测试。
 *
 * <p>覆盖需求文档第 6 章的全部计费规则：按次计费、按小时计费（分段 / 取整 / 加班倍率）、
 * 跨 0 点、起算点边界、取整边界、金额精度、异常入参与月度汇总。</p>
 */
class BillingEngineTest {

    // ==================== 测试辅助 ====================

    /** 构造按小时计费入参 */
    private static BillingInput hourInput(String start, String end, String overtimeStart,
                                          String unitPrice, String multiplier, RoundingRule rule) {
        return BillingInput.builder()
                .billingMode(BillingMode.BY_HOUR)
                .unitPrice(new BigDecimal(unitPrice))
                .startTime(start)
                .endTime(end)
                .overtimeStart(overtimeStart)
                .overtimeMultiplier(new BigDecimal(multiplier))
                .roundingRule(rule)
                .build();
    }

    /** 构造按次计费入参 */
    private static BillingInput countInput(String unitPrice) {
        return BillingInput.builder()
                .billingMode(BillingMode.BY_COUNT)
                .unitPrice(new BigDecimal(unitPrice))
                .startTime("09:00")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build();
    }

    /** 金额断言：用 compareTo 比较，忽略 scale 差异 */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual, "金额不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "金额期望 " + expected + "，实际 " + actual);
    }

    /** 小时数断言：用 compareTo 比较，忽略 scale 差异 */
    private static void assertHours(String expected, BigDecimal actual) {
        assertNotNull(actual, "小时数不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "小时数期望 " + expected + "，实际 " + actual);
    }

    // ==================== 一、文档示例（4 个） ====================

    @Test
    @DisplayName("文档示例1：17:30~19:30，80元/h，EXACT → 正常40 + 加班240 = 280")
    void example1Exact() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:30", "18:00", "80", "2", RoundingRule.EXACT));

        assertEquals(BillingMode.BY_HOUR, result.getBillingMode());
        assertEquals(0, result.getCount());
        assertHours("0.5", result.getNormalHours());
        assertHours("1.5", result.getOvertimeHours());
        assertMoney("40.00", result.getNormalAmount());
        assertMoney("240.00", result.getOvertimeAmount());
        assertMoney("280.00", result.getTotal());
        assertHours("0.5", result.getRawNormalHours());
        assertHours("1.5", result.getRawOvertimeHours());
        assertFalse(result.isCrossMidnight());
        assertEquals("正常段 17:30~18:00：原始 30min × 80 = 40 元"
                        + "；加班段 18:00~19:30：原始 1h30m × 80 × 2 = 240 元"
                        + "；合计 280 元",
                result.getExplanation());
    }

    @Test
    @DisplayName("文档示例2：17:30~19:40，80元/h，HALF_HOUR_UP → 正常40 + 加班320 = 360")
    void example2HalfHourUp() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:40", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertHours("0.5", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertMoney("40.00", result.getNormalAmount());
        assertMoney("320.00", result.getOvertimeAmount());
        assertMoney("360.00", result.getTotal());
        assertEquals("正常段 17:30~18:00：原始 30min → 取整 0.5h × 80 = 40 元"
                        + "；加班段 18:00~19:40：原始 1h40m → 取整 2h × 80 × 2 = 320 元"
                        + "；合计 360 元",
                result.getExplanation());
    }

    @Test
    @DisplayName("文档示例3：按次 50元/次，白天与夜间各一次 → 各 50")
    void example3ByCount() {
        BillingResult day = BillingEngine.calculate(countInput("50"));
        BillingResult night = BillingEngine.calculate(BillingInput.builder()
                .billingMode(BillingMode.BY_COUNT)
                .unitPrice(new BigDecimal("50"))
                .startTime("22:00")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build());

        assertMoney("50.00", day.getTotal());
        assertMoney("50.00", night.getTotal());
        assertEquals(1, day.getCount());
        assertEquals(1, night.getCount());
        assertMoney("50.00", day.getNormalAmount());
        assertMoney("0.00", day.getOvertimeAmount());
        assertFalse(day.isCrossMidnight());
        assertFalse(night.isCrossMidnight());
        assertEquals("按次计费：1 次 × 50 元/次 = 50 元（按次不参与加班与取整）", day.getExplanation());
    }

    @Test
    @DisplayName("文档示例4：月度汇总 12次×50 + 30h×80 + 6h×80×2 = 3960")
    void example4MonthlySummary() {
        MonthlySummary summary = MonthlySummary.empty();

        for (int i = 0; i < 12; i++) {
            BillingEngine.accumulate(summary, BillingEngine.calculate(countInput("50")));
        }
        for (int i = 0; i < 5; i++) {
            BillingEngine.accumulate(summary, BillingEngine.calculate(
                    hourInput("09:00", "15:00", "18:00", "80", "2", RoundingRule.EXACT)));
        }
        BillingEngine.accumulate(summary, BillingEngine.calculate(
                hourInput("18:00", "00:00", "18:00", "80", "2", RoundingRule.EXACT)));

        assertEquals(18, summary.getRecordCount());
        assertEquals(12, summary.getCountTotal());
        assertHours("30", summary.getNormalHoursTotal());
        assertHours("6", summary.getOvertimeHoursTotal());
        assertMoney("600.00", summary.getByCountAmount());
        assertMoney("3360.00", summary.getByHourAmount());
        assertMoney("3960.00", summary.getTotalAmount());
    }

    // ==================== 二、按次计费（4 个） ====================

    @Test
    @DisplayName("按次-白天：09:00 一次，50元/次 → 50")
    void byCountDaytime() {
        BillingResult result = BillingEngine.calculate(countInput("50"));

        assertEquals(BillingMode.BY_COUNT, result.getBillingMode());
        assertEquals(1, result.getCount());
        assertMoney("50.00", result.getTotal());
        assertMoney("50.00", result.getNormalAmount());
        assertMoney("0.00", result.getOvertimeAmount());
        assertHours("0", result.getNormalHours());
        assertHours("0", result.getOvertimeHours());
        assertHours("0", result.getRawNormalHours());
        assertHours("0", result.getRawOvertimeHours());
        assertFalse(result.isCrossMidnight());
    }

    @Test
    @DisplayName("按次-夜间：22:00 一次，不参与加班，仍为 50")
    void byCountNight() {
        BillingResult result = BillingEngine.calculate(BillingInput.builder()
                .billingMode(BillingMode.BY_COUNT)
                .unitPrice(new BigDecimal("50"))
                .startTime("22:00")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build());

        assertEquals(1, result.getCount());
        assertMoney("50.00", result.getTotal());
        assertMoney("0.00", result.getOvertimeAmount());
        assertFalse(result.isCrossMidnight());
    }

    @Test
    @DisplayName("按次-小数单价：33.335元/次 → 四舍五入到分 33.34")
    void byCountDecimalUnitPrice() {
        BillingResult result = BillingEngine.calculate(countInput("33.335"));

        assertEquals(1, result.getCount());
        assertMoney("33.34", result.getTotal());
        assertMoney("33.34", result.getNormalAmount());
        assertEquals("按次计费：1 次 × 33.335 元/次 = 33.34 元（按次不参与加班与取整）",
                result.getExplanation());
    }

    @Test
    @DisplayName("按次-取整规则下仍按次：HOUR_UP 不影响按次金额")
    void byCountIgnoresRoundingRule() {
        BillingResult result = BillingEngine.calculate(BillingInput.builder()
                .billingMode(BillingMode.BY_COUNT)
                .unitPrice(new BigDecimal("50"))
                .startTime("09:00")
                .endTime("09:01")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.HOUR_UP)
                .build());

        assertEquals(1, result.getCount());
        assertMoney("50.00", result.getTotal());
        assertHours("0", result.getNormalHours());
        assertHours("0", result.getOvertimeHours());
    }

    // ==================== 三、按小时 EXACT（5 个） ====================

    @Test
    @DisplayName("EXACT-全正常段：09:00~12:00，80元/h → 240")
    void exactAllNormal() {
        BillingResult result = BillingEngine.calculate(
                hourInput("09:00", "12:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("3", result.getNormalHours());
        assertHours("0", result.getOvertimeHours());
        assertMoney("240.00", result.getNormalAmount());
        assertMoney("0.00", result.getOvertimeAmount());
        assertMoney("240.00", result.getTotal());
        assertFalse(result.isCrossMidnight());
        assertTrue(result.getExplanation().contains("无加班时段"));
    }

    @Test
    @DisplayName("EXACT-全加班段：18:00~21:00，80元/h×2 → 480")
    void exactAllOvertime() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "21:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("0", result.getNormalHours());
        assertHours("3", result.getOvertimeHours());
        assertMoney("0.00", result.getNormalAmount());
        assertMoney("480.00", result.getOvertimeAmount());
        assertMoney("480.00", result.getTotal());
        assertFalse(result.isCrossMidnight());
    }

    @Test
    @DisplayName("EXACT-跨0点：22:00~01:00，连续服务归入加班段 → 480")
    void exactCrossMidnight() {
        BillingResult result = BillingEngine.calculate(
                hourInput("22:00", "01:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertTrue(result.isCrossMidnight());
        assertHours("0", result.getNormalHours());
        assertHours("3", result.getOvertimeHours());
        assertMoney("0.00", result.getNormalAmount());
        assertMoney("480.00", result.getOvertimeAmount());
        assertMoney("480.00", result.getTotal());
    }

    @Test
    @DisplayName("EXACT-跨0点且跨起算点：17:00~次日01:00 → 80 + 1120 = 1200")
    void exactCrossMidnightAndOvertimeStart() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:00", "01:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertTrue(result.isCrossMidnight());
        assertHours("1", result.getNormalHours());
        assertHours("7", result.getOvertimeHours());
        assertMoney("80.00", result.getNormalAmount());
        assertMoney("1120.00", result.getOvertimeAmount());
        assertMoney("1200.00", result.getTotal());
    }

    @Test
    @DisplayName("EXACT-边界：17:00~19:00 恰好各 1 小时 → 80 + 160 = 240")
    void exactSplitBoundary() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:00", "19:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("1", result.getNormalHours());
        assertHours("1", result.getOvertimeHours());
        assertMoney("80.00", result.getNormalAmount());
        assertMoney("160.00", result.getOvertimeAmount());
        assertMoney("240.00", result.getTotal());
        assertFalse(result.isCrossMidnight());
    }

    // ==================== 四、按小时 HALF_HOUR_UP（5 个） ====================

    @Test
    @DisplayName("HALF_HOUR_UP-示例2：17:30~19:40 → 加班 1h40m 补足为 2h")
    void halfHourUpExample2() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:40", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertHours("0.5", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertHours("0.5", result.getRawNormalHours());
        assertHours("1.666667", result.getRawOvertimeHours());
        assertMoney("360.00", result.getTotal());
    }

    @Test
    @DisplayName("HALF_HOUR_UP-整除边界：17:30~19:30，90 分钟已是 30 的倍数不补足")
    void halfHourUpExactMultiple() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:30", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertHours("0.5", result.getNormalHours());
        assertHours("1.5", result.getOvertimeHours());
        assertMoney("40.00", result.getNormalAmount());
        assertMoney("240.00", result.getOvertimeAmount());
        assertMoney("280.00", result.getTotal());
    }

    @Test
    @DisplayName("HALF_HOUR_UP-1分钟补齐：17:30~19:31，91 分钟补足为 120 分钟")
    void halfHourUpOneMinutePadding() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:31", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertHours("0.5", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertHours("1.516667", result.getRawOvertimeHours());
        assertMoney("320.00", result.getOvertimeAmount());
        assertMoney("360.00", result.getTotal());
    }

    @Test
    @DisplayName("HALF_HOUR_UP-单价95：17:30~19:40 → 47.50 + 380 = 427.50")
    void halfHourUpUnitPrice95() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:40", "18:00", "95", "2", RoundingRule.HALF_HOUR_UP));

        assertMoney("47.50", result.getNormalAmount());
        assertMoney("380.00", result.getOvertimeAmount());
        assertMoney("427.50", result.getTotal());
    }

    @Test
    @DisplayName("HALF_HOUR_UP-跨0点：22:00~01:10，190 分钟补足为 210 分钟 → 560")
    void halfHourUpCrossMidnight() {
        BillingResult result = BillingEngine.calculate(
                hourInput("22:00", "01:10", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertTrue(result.isCrossMidnight());
        assertHours("0", result.getNormalHours());
        assertHours("3.5", result.getOvertimeHours());
        assertHours("3.166667", result.getRawOvertimeHours());
        assertMoney("560.00", result.getTotal());
    }

    // ==================== 五、按小时 HOUR_UP（4 个） ====================

    @Test
    @DisplayName("HOUR_UP-示例2变体：17:30~19:40 → 正常 30 分钟补足 1h，加班 100 分钟补足 2h")
    void hourUpExample2Variant() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "19:40", "18:00", "80", "2", RoundingRule.HOUR_UP));

        assertHours("1", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertHours("0.5", result.getRawNormalHours());
        assertHours("1.666667", result.getRawOvertimeHours());
        assertMoney("80.00", result.getNormalAmount());
        assertMoney("320.00", result.getOvertimeAmount());
        assertMoney("400.00", result.getTotal());
    }

    @Test
    @DisplayName("HOUR_UP-整除边界：18:00~20:00，120 分钟已是整小时不补足 → 320")
    void hourUpExactMultiple() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "20:00", "18:00", "80", "2", RoundingRule.HOUR_UP));

        assertHours("0", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertMoney("320.00", result.getOvertimeAmount());
        assertMoney("320.00", result.getTotal());
    }

    @Test
    @DisplayName("HOUR_UP-1分钟：18:00~20:01，121 分钟补足为 180 分钟 → 480")
    void hourUpOneMinutePadding() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "20:01", "18:00", "80", "2", RoundingRule.HOUR_UP));

        assertHours("0", result.getNormalHours());
        assertHours("3", result.getOvertimeHours());
        assertHours("2.016667", result.getRawOvertimeHours());
        assertMoney("480.00", result.getOvertimeAmount());
        assertMoney("480.00", result.getTotal());
    }

    @Test
    @DisplayName("HOUR_UP-自定义起算20:00倍率1.5：18:00~22:00，100元/h → 200 + 300 = 500")
    void hourUpCustomOvertimeStartAndMultiplier() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "22:00", "20:00", "100", "1.5", RoundingRule.HOUR_UP));

        assertHours("2", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertMoney("200.00", result.getNormalAmount());
        assertMoney("300.00", result.getOvertimeAmount());
        assertMoney("500.00", result.getTotal());
        assertFalse(result.isCrossMidnight());
    }

    // ==================== 六、边界（3 个） ====================

    @Test
    @DisplayName("边界-恰好结束在起算点：17:30~18:00 → 全部正常 40，无加班")
    void boundaryEndAtOvertimeStart() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "18:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("0.5", result.getNormalHours());
        assertHours("0", result.getOvertimeHours());
        assertMoney("40.00", result.getNormalAmount());
        assertMoney("0.00", result.getOvertimeAmount());
        assertMoney("40.00", result.getTotal());
        assertTrue(result.getExplanation().contains("无加班时段"));
    }

    @Test
    @DisplayName("边界-恰好开始在起算点：18:00~19:00 → 全部加班 160")
    void boundaryStartAtOvertimeStart() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "19:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("0", result.getNormalHours());
        assertHours("1", result.getOvertimeHours());
        assertMoney("0.00", result.getNormalAmount());
        assertMoney("160.00", result.getOvertimeAmount());
        assertMoney("160.00", result.getTotal());
    }

    @Test
    @DisplayName("边界-超长跨日：08:00~次日08:00 共 24h → 10h 正常 + 14h 加班 = 3040")
    void boundaryFullDayCrossMidnight() {
        BillingResult result = BillingEngine.calculate(
                hourInput("08:00", "08:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertTrue(result.isCrossMidnight());
        assertHours("10", result.getNormalHours());
        assertHours("14", result.getOvertimeHours());
        assertMoney("800.00", result.getNormalAmount());
        assertMoney("2240.00", result.getOvertimeAmount());
        assertMoney("3040.00", result.getTotal());
    }

    // ==================== 七、取整函数（4 个） ====================

    @Test
    @DisplayName("取整-EXACT：原样返回分钟数")
    void roundingExact() {
        assertEquals(1, BillingEngine.applyRounding(1, RoundingRule.EXACT));
        assertEquals(37, BillingEngine.applyRounding(37, RoundingRule.EXACT));
        assertEquals(59, BillingEngine.applyRounding(59, RoundingRule.EXACT));
        assertEquals(60, BillingEngine.applyRounding(60, RoundingRule.EXACT));
        assertEquals(61, BillingEngine.applyRounding(61, RoundingRule.EXACT));
        assertEquals(1439, BillingEngine.applyRounding(1439, RoundingRule.EXACT));
    }

    @Test
    @DisplayName("取整-HALF_HOUR_UP：按 30 分钟向上补足")
    void roundingHalfHourUp() {
        assertEquals(30, BillingEngine.applyRounding(1, RoundingRule.HALF_HOUR_UP));
        assertEquals(30, BillingEngine.applyRounding(29, RoundingRule.HALF_HOUR_UP));
        assertEquals(30, BillingEngine.applyRounding(30, RoundingRule.HALF_HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(31, RoundingRule.HALF_HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(59, RoundingRule.HALF_HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(60, RoundingRule.HALF_HOUR_UP));
        assertEquals(90, BillingEngine.applyRounding(61, RoundingRule.HALF_HOUR_UP));
        assertEquals(90, BillingEngine.applyRounding(90, RoundingRule.HALF_HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(91, RoundingRule.HALF_HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(100, RoundingRule.HALF_HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(120, RoundingRule.HALF_HOUR_UP));
        assertEquals(150, BillingEngine.applyRounding(121, RoundingRule.HALF_HOUR_UP));
        assertEquals(1440, BillingEngine.applyRounding(1439, RoundingRule.HALF_HOUR_UP));
    }

    @Test
    @DisplayName("取整-HOUR_UP：按 60 分钟向上补足")
    void roundingHourUp() {
        assertEquals(60, BillingEngine.applyRounding(1, RoundingRule.HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(29, RoundingRule.HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(30, RoundingRule.HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(59, RoundingRule.HOUR_UP));
        assertEquals(60, BillingEngine.applyRounding(60, RoundingRule.HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(61, RoundingRule.HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(90, RoundingRule.HOUR_UP));
        assertEquals(120, BillingEngine.applyRounding(120, RoundingRule.HOUR_UP));
        assertEquals(180, BillingEngine.applyRounding(121, RoundingRule.HOUR_UP));
        assertEquals(1440, BillingEngine.applyRounding(1439, RoundingRule.HOUR_UP));
    }

    @Test
    @DisplayName("取整-0和负数：任何规则都返回 0")
    void roundingZeroAndNegative() {
        for (RoundingRule rule : RoundingRule.values()) {
            assertEquals(0, BillingEngine.applyRounding(0, rule), "规则 " + rule + " 的 0 分钟");
            assertEquals(0, BillingEngine.applyRounding(-1, rule), "规则 " + rule + " 的 -1 分钟");
            assertEquals(0, BillingEngine.applyRounding(-5, rule), "规则 " + rule + " 的 -5 分钟");
            assertEquals(0, BillingEngine.applyRounding(-100, rule), "规则 " + rule + " 的 -100 分钟");
        }
    }

    // ==================== 八、时间工具（3 个） ====================

    @Test
    @DisplayName("时间工具-parseTimeToMinutes 正常：HH:mm 转当日分钟数")
    void parseTimeToMinutesNormal() {
        assertEquals(0, BillingEngine.parseTimeToMinutes("00:00"));
        assertEquals(485, BillingEngine.parseTimeToMinutes("08:05"));
        assertEquals(485, BillingEngine.parseTimeToMinutes("8:05"));
        assertEquals(485, BillingEngine.parseTimeToMinutes(" 08:05 "));
        assertEquals(570, BillingEngine.parseTimeToMinutes("09:30"));
        assertEquals(1050, BillingEngine.parseTimeToMinutes("17:30"));
        assertEquals(1439, BillingEngine.parseTimeToMinutes("23:59"));
    }

    @Test
    @DisplayName("时间工具-parseTimeToMinutes 非法：格式错误与超范围均抛异常")
    void parseTimeToMinutesInvalid() {
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes("24:00"));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes("12:60"));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes("12:5"));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes("12-30"));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes("abc"));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes(""));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.parseTimeToMinutes(null));
    }

    @Test
    @DisplayName("时间工具-minutesToTime 跨日回绕：负数与超过 1440 均归一化")
    void minutesToTimeWrapping() {
        assertEquals("00:00", BillingEngine.minutesToTime(0));
        assertEquals("09:30", BillingEngine.minutesToTime(570));
        assertEquals("17:30", BillingEngine.minutesToTime(1050));
        assertEquals("23:59", BillingEngine.minutesToTime(1439));
        assertEquals("00:00", BillingEngine.minutesToTime(1440));
        assertEquals("01:00", BillingEngine.minutesToTime(1500));
        assertEquals("00:00", BillingEngine.minutesToTime(2880));
        assertEquals("23:00", BillingEngine.minutesToTime(-60));
        assertEquals("23:59", BillingEngine.minutesToTime(-1));
    }

    // ==================== 九、精度（2 个） ====================

    @Test
    @DisplayName("精度-1h40m×85×2 = 283.33（不是 283.34）")
    void precisionOneHourFortyMinutes() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "19:40", "18:00", "85", "2", RoundingRule.EXACT));

        assertHours("1.666667", result.getOvertimeHours());
        assertMoney("283.33", result.getOvertimeAmount());
        assertMoney("283.33", result.getTotal());
        assertTrue(new BigDecimal("283.34").compareTo(result.getTotal()) != 0,
                "金额不应被错误进位为 283.34");
        assertEquals(2, result.getTotal().scale(), "金额应保留 2 位小数");
        assertEquals(6, result.getOvertimeHours().scale(), "小时数应保留 6 位小数");
    }

    @Test
    @DisplayName("精度-浮点安全：0.1 + 0.2 精确等于 0.30，3h×0.1 精确等于 0.30")
    void precisionFloatSafety() {
        MonthlySummary summary = MonthlySummary.empty();
        BillingEngine.accumulate(summary, BillingEngine.calculate(countInput("0.1")));
        BillingEngine.accumulate(summary, BillingEngine.calculate(countInput("0.2")));

        assertMoney("0.30", summary.getByCountAmount());
        assertMoney("0.30", summary.getTotalAmount());

        BillingResult hourly = BillingEngine.calculate(
                hourInput("09:00", "12:00", "18:00", "0.1", "2", RoundingRule.EXACT));
        assertMoney("0.30", hourly.getNormalAmount());
        assertMoney("0.30", hourly.getTotal());
    }

    // ==================== 十、异常（3 个） ====================

    @Test
    @DisplayName("异常-按小时缺结束时间：null 与空串均抛异常")
    void exceptionMissingEndTime() {
        BillingInput nullEnd = BillingInput.builder()
                .billingMode(BillingMode.BY_HOUR)
                .unitPrice(new BigDecimal("80"))
                .startTime("17:30")
                .endTime(null)
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build();
        BillingInput blankEnd = BillingInput.builder()
                .billingMode(BillingMode.BY_HOUR)
                .unitPrice(new BigDecimal("80"))
                .startTime("17:30")
                .endTime("   ")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build();

        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(nullEnd));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(blankEnd));
    }

    @Test
    @DisplayName("异常-单价为负：按次与按小时均抛异常")
    void exceptionNegativeUnitPrice() {
        BillingInput byCount = BillingInput.builder()
                .billingMode(BillingMode.BY_COUNT)
                .unitPrice(new BigDecimal("-1"))
                .startTime("09:00")
                .overtimeStart("18:00")
                .overtimeMultiplier(new BigDecimal("2"))
                .roundingRule(RoundingRule.EXACT)
                .build();
        BillingInput byHour = hourInput("17:30", "19:30", "18:00", "-0.01", "2", RoundingRule.EXACT);

        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(byCount));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(byHour));
    }

    @Test
    @DisplayName("异常-时间格式非法：开始/结束/起算时间任一非法均抛异常")
    void exceptionInvalidTimeFormat() {
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(
                hourInput("25:00", "19:30", "18:00", "80", "2", RoundingRule.EXACT)));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(
                hourInput("17:30", "12:5", "18:00", "80", "2", RoundingRule.EXACT)));
        assertThrows(IllegalArgumentException.class, () -> BillingEngine.calculate(
                hourInput("17:30", "19:30", "abc", "80", "2", RoundingRule.EXACT)));
    }

    // ==================== 十一、汇总（2 个） ====================

    @Test
    @DisplayName("汇总-累加正确：按次与按小时分别归集，合计同步更新")
    void summaryAccumulate() {
        MonthlySummary summary = MonthlySummary.empty();
        assertEquals(0, summary.getRecordCount());
        assertEquals(0, summary.getCountTotal());
        assertHours("0", summary.getNormalHoursTotal());
        assertHours("0", summary.getOvertimeHoursTotal());
        assertMoney("0", summary.getByCountAmount());
        assertMoney("0", summary.getByHourAmount());
        assertMoney("0", summary.getTotalAmount());

        BillingEngine.accumulate(summary, BillingEngine.calculate(countInput("50")));
        assertEquals(1, summary.getRecordCount());
        assertEquals(1, summary.getCountTotal());
        assertMoney("50.00", summary.getByCountAmount());
        assertMoney("0.00", summary.getByHourAmount());
        assertMoney("50.00", summary.getTotalAmount());

        BillingEngine.accumulate(summary, BillingEngine.calculate(
                hourInput("17:30", "19:30", "18:00", "80", "2", RoundingRule.EXACT)));
        assertEquals(2, summary.getRecordCount());
        assertEquals(1, summary.getCountTotal());
        assertHours("0.5", summary.getNormalHoursTotal());
        assertHours("1.5", summary.getOvertimeHoursTotal());
        assertMoney("50.00", summary.getByCountAmount());
        assertMoney("280.00", summary.getByHourAmount());
        assertMoney("330.00", summary.getTotalAmount());
    }

    @Test
    @DisplayName("汇总-示例4完整验证：12次×50 + 30h×80 + 6h×80×2 = 3960")
    void summaryExample4Full() {
        MonthlySummary summary = MonthlySummary.empty();

        for (int i = 0; i < 12; i++) {
            BillingEngine.accumulate(summary, BillingEngine.calculate(countInput("50")));
        }
        for (int i = 0; i < 5; i++) {
            BillingEngine.accumulate(summary, BillingEngine.calculate(
                    hourInput("09:00", "15:00", "18:00", "80", "2", RoundingRule.EXACT)));
        }
        BillingEngine.accumulate(summary, BillingEngine.calculate(
                hourInput("18:00", "00:00", "18:00", "80", "2", RoundingRule.EXACT)));

        assertEquals(18, summary.getRecordCount());
        assertEquals(12, summary.getCountTotal());
        assertHours("30", summary.getNormalHoursTotal());
        assertHours("6", summary.getOvertimeHoursTotal());
        assertMoney("600.00", summary.getByCountAmount());
        assertMoney("3360.00", summary.getByHourAmount());
        assertMoney("3960.00", summary.getTotalAmount());
    }

    // ==================== 十二、跨 0 点专项（2 个） ====================

    @Test
    @DisplayName("跨0点专项-22:00~01:00 全加班：100元/h×2 → 600")
    void crossMidnightAllOvertime() {
        BillingResult result = BillingEngine.calculate(
                hourInput("22:00", "01:00", "18:00", "100", "2", RoundingRule.EXACT));

        assertTrue(result.isCrossMidnight());
        assertHours("0", result.getNormalHours());
        assertHours("3", result.getOvertimeHours());
        assertMoney("0.00", result.getNormalAmount());
        assertMoney("600.00", result.getOvertimeAmount());
        assertMoney("600.00", result.getTotal());
    }

    @Test
    @DisplayName("跨0点专项-17:00~次日01:00 分段：1h 正常 + 7h 加班 → 100 + 1400 = 1500")
    void crossMidnightSplit() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:00", "01:00", "18:00", "100", "2", RoundingRule.EXACT));

        assertTrue(result.isCrossMidnight());
        assertHours("1", result.getNormalHours());
        assertHours("7", result.getOvertimeHours());
        assertMoney("100.00", result.getNormalAmount());
        assertMoney("1400.00", result.getOvertimeAmount());
        assertMoney("1500.00", result.getTotal());
    }

    // ==================== 十三、起算点边界专项（2 个） ====================

    @Test
    @DisplayName("起算点边界专项-18:00~20:00 全加班：2h×80×2 → 320")
    void overtimeStartBoundaryAllOvertime() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "20:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("0", result.getNormalHours());
        assertHours("2", result.getOvertimeHours());
        assertMoney("0.00", result.getNormalAmount());
        assertMoney("320.00", result.getOvertimeAmount());
        assertMoney("320.00", result.getTotal());
    }

    @Test
    @DisplayName("起算点边界专项-16:00~18:00 全正常：2h×80 → 160")
    void overtimeStartBoundaryAllNormal() {
        BillingResult result = BillingEngine.calculate(
                hourInput("16:00", "18:00", "18:00", "80", "2", RoundingRule.EXACT));

        assertHours("2", result.getNormalHours());
        assertHours("0", result.getOvertimeHours());
        assertMoney("160.00", result.getNormalAmount());
        assertMoney("0.00", result.getOvertimeAmount());
        assertMoney("160.00", result.getTotal());
    }

    // ==================== 十四、取整边界专项（2 个） ====================

    @Test
    @DisplayName("取整边界专项-30分钟 HALF_HOUR_UP 不补足：17:30~18:00 → 40")
    void roundingBoundaryHalfHourNoPadding() {
        BillingResult result = BillingEngine.calculate(
                hourInput("17:30", "18:00", "18:00", "80", "2", RoundingRule.HALF_HOUR_UP));

        assertHours("0.5", result.getNormalHours());
        assertMoney("40.00", result.getNormalAmount());
        assertMoney("40.00", result.getTotal());
    }

    @Test
    @DisplayName("取整边界专项-60分钟 HOUR_UP 不补足：18:00~19:00 → 160")
    void roundingBoundaryHourNoPadding() {
        BillingResult result = BillingEngine.calculate(
                hourInput("18:00", "19:00", "18:00", "80", "2", RoundingRule.HOUR_UP));

        assertHours("1", result.getOvertimeHours());
        assertMoney("160.00", result.getOvertimeAmount());
        assertMoney("160.00", result.getTotal());
    }

    // ==================== 十五、时长文本（1 个） ====================

    @Test
    @DisplayName("时长文本-minutesText：0h / 30min / 1h / 1h40m")
    void minutesTextFormatting() {
        assertEquals("0h", BillingEngine.minutesText(0));
        assertEquals("0h", BillingEngine.minutesText(-5));
        assertEquals("1min", BillingEngine.minutesText(1));
        assertEquals("30min", BillingEngine.minutesText(30));
        assertEquals("1h", BillingEngine.minutesText(60));
        assertEquals("1h30m", BillingEngine.minutesText(90));
        assertEquals("1h40m", BillingEngine.minutesText(100));
        assertEquals("24h", BillingEngine.minutesText(1440));
    }
}
