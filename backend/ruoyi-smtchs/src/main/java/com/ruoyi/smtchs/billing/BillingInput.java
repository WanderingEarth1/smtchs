package com.ruoyi.smtchs.billing;

import java.math.BigDecimal;

/**
 * 计费入参（对应 TS 源码 BillingInput 接口）。
 *
 * <p>使用 Builder 模式构造：</p>
 * <pre>
 * BillingInput input = BillingInput.builder()
 *         .billingMode(BillingMode.BY_HOUR)
 *         .unitPrice(new BigDecimal("80"))
 *         .startTime("17:30")
 *         .endTime("19:30")
 *         .overtimeStart("18:00")
 *         .overtimeMultiplier(new BigDecimal("2"))
 *         .roundingRule(RoundingRule.EXACT)
 *         .build();
 * </pre>
 */
public class BillingInput {

    /** 计费方式 */
    private BillingMode billingMode;

    /** 单价：元/次 或 元/小时 */
    private BigDecimal unitPrice;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm，按次类型可省略 */
    private String endTime;

    /** 加班起算时间 HH:mm */
    private String overtimeStart;

    /** 加班倍率 */
    private BigDecimal overtimeMultiplier;

    /** 取整规则 */
    private RoundingRule roundingRule;

    public BillingInput() {
        this.overtimeMultiplier = BigDecimal.ONE;
        this.roundingRule = RoundingRule.EXACT;
    }

    private BillingInput(Builder builder) {
        this.billingMode = builder.billingMode;
        this.unitPrice = builder.unitPrice;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.overtimeStart = builder.overtimeStart;
        this.overtimeMultiplier = builder.overtimeMultiplier == null ? BigDecimal.ONE : builder.overtimeMultiplier;
        this.roundingRule = builder.roundingRule == null ? RoundingRule.EXACT : builder.roundingRule;
    }

    public static Builder builder() {
        return new Builder();
    }

    public BillingMode getBillingMode() {
        return billingMode;
    }

    public void setBillingMode(BillingMode billingMode) {
        this.billingMode = billingMode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getOvertimeStart() {
        return overtimeStart;
    }

    public void setOvertimeStart(String overtimeStart) {
        this.overtimeStart = overtimeStart;
    }

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) {
        this.overtimeMultiplier = overtimeMultiplier;
    }

    public RoundingRule getRoundingRule() {
        return roundingRule;
    }

    public void setRoundingRule(RoundingRule roundingRule) {
        this.roundingRule = roundingRule;
    }

    @Override
    public String toString() {
        return "BillingInput{"
                + "billingMode=" + billingMode
                + ", unitPrice=" + unitPrice
                + ", startTime='" + startTime + '\''
                + ", endTime='" + endTime + '\''
                + ", overtimeStart='" + overtimeStart + '\''
                + ", overtimeMultiplier=" + overtimeMultiplier
                + ", roundingRule=" + roundingRule
                + '}';
    }

    /**
     * BillingInput 构造器。
     */
    public static class Builder {

        private BillingMode billingMode;
        private BigDecimal unitPrice;
        private String startTime;
        private String endTime;
        private String overtimeStart;
        private BigDecimal overtimeMultiplier = BigDecimal.ONE;
        private RoundingRule roundingRule = RoundingRule.EXACT;

        public Builder billingMode(BillingMode billingMode) {
            this.billingMode = billingMode;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder startTime(String startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(String endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder overtimeStart(String overtimeStart) {
            this.overtimeStart = overtimeStart;
            return this;
        }

        public Builder overtimeMultiplier(BigDecimal overtimeMultiplier) {
            this.overtimeMultiplier = overtimeMultiplier;
            return this;
        }

        public Builder roundingRule(RoundingRule roundingRule) {
            this.roundingRule = roundingRule;
            return this;
        }

        public BillingInput build() {
            return new BillingInput(this);
        }
    }
}
