package com.ruoyi.smtchs.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单条记录的计费结果（对应 TS 源码 BillingResult）。
 *
 * <p>不可变对象：所有字段在构造时定型，金额统一保留 2 位小数，
 * 小时数统一保留 6 位小数，便于用 {@code equals} / {@code compareTo} 断言。</p>
 */
public final class BillingResult {

    /** 小时数保留位数（对应 TS 的 tidy：约 3.6 毫秒精度） */
    public static final int HOURS_SCALE = 6;

    /** 金额保留位数（分） */
    public static final int MONEY_SCALE = 2;

    private final BillingMode billingMode;
    private final int count;
    private final BigDecimal normalHours;
    private final BigDecimal overtimeHours;
    private final BigDecimal normalAmount;
    private final BigDecimal overtimeAmount;
    private final BigDecimal total;
    private final BigDecimal rawNormalHours;
    private final BigDecimal rawOvertimeHours;
    private final boolean crossMidnight;
    private final String explanation;

    private BillingResult(Builder builder) {
        this.billingMode = builder.billingMode;
        this.count = builder.count;
        this.normalHours = scaleHours(builder.normalHours);
        this.overtimeHours = scaleHours(builder.overtimeHours);
        this.normalAmount = scaleMoney(builder.normalAmount);
        this.overtimeAmount = scaleMoney(builder.overtimeAmount);
        this.total = scaleMoney(builder.total);
        this.rawNormalHours = scaleHours(builder.rawNormalHours);
        this.rawOvertimeHours = scaleHours(builder.rawOvertimeHours);
        this.crossMidnight = builder.crossMidnight;
        this.explanation = builder.explanation;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static BigDecimal scaleHours(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(HOURS_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BillingMode getBillingMode() {
        return billingMode;
    }

    public int getCount() {
        return count;
    }

    public BigDecimal getNormalHours() {
        return normalHours;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public BigDecimal getNormalAmount() {
        return normalAmount;
    }

    public BigDecimal getOvertimeAmount() {
        return overtimeAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getRawNormalHours() {
        return rawNormalHours;
    }

    public BigDecimal getRawOvertimeHours() {
        return rawOvertimeHours;
    }

    public boolean isCrossMidnight() {
        return crossMidnight;
    }

    public String getExplanation() {
        return explanation;
    }

    @Override
    public String toString() {
        return "BillingResult{"
                + "billingMode=" + billingMode
                + ", count=" + count
                + ", normalHours=" + normalHours
                + ", overtimeHours=" + overtimeHours
                + ", normalAmount=" + normalAmount
                + ", overtimeAmount=" + overtimeAmount
                + ", total=" + total
                + ", rawNormalHours=" + rawNormalHours
                + ", rawOvertimeHours=" + rawOvertimeHours
                + ", crossMidnight=" + crossMidnight
                + ", explanation='" + explanation + '\''
                + '}';
    }

    /**
     * BillingResult 构造器。
     */
    public static class Builder {

        private BillingMode billingMode;
        private int count;
        private BigDecimal normalHours = BigDecimal.ZERO;
        private BigDecimal overtimeHours = BigDecimal.ZERO;
        private BigDecimal normalAmount = BigDecimal.ZERO;
        private BigDecimal overtimeAmount = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal rawNormalHours = BigDecimal.ZERO;
        private BigDecimal rawOvertimeHours = BigDecimal.ZERO;
        private boolean crossMidnight;
        private String explanation;

        public Builder billingMode(BillingMode billingMode) {
            this.billingMode = billingMode;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder normalHours(BigDecimal normalHours) {
            this.normalHours = normalHours;
            return this;
        }

        public Builder overtimeHours(BigDecimal overtimeHours) {
            this.overtimeHours = overtimeHours;
            return this;
        }

        public Builder normalAmount(BigDecimal normalAmount) {
            this.normalAmount = normalAmount;
            return this;
        }

        public Builder overtimeAmount(BigDecimal overtimeAmount) {
            this.overtimeAmount = overtimeAmount;
            return this;
        }

        public Builder total(BigDecimal total) {
            this.total = total;
            return this;
        }

        public Builder rawNormalHours(BigDecimal rawNormalHours) {
            this.rawNormalHours = rawNormalHours;
            return this;
        }

        public Builder rawOvertimeHours(BigDecimal rawOvertimeHours) {
            this.rawOvertimeHours = rawOvertimeHours;
            return this;
        }

        public Builder crossMidnight(boolean crossMidnight) {
            this.crossMidnight = crossMidnight;
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public BillingResult build() {
            return new BillingResult(this);
        }
    }
}
