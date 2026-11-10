package com.ruoyi.smtchs.billing;

import java.math.BigDecimal;

/**
 * 月度汇总（对应 TS 源码 MonthlySummary）。
 *
 * <p>可变对象，用于逐条累加。</p>
 */
public class MonthlySummary {

    /** 记录条数 */
    private int recordCount;

    /** 按次次数合计 */
    private int countTotal;

    /** 正常时长合计（小时） */
    private BigDecimal normalHoursTotal;

    /** 加班时长合计（小时） */
    private BigDecimal overtimeHoursTotal;

    /** 按次金额合计 */
    private BigDecimal byCountAmount;

    /** 按时金额合计 */
    private BigDecimal byHourAmount;

    /** 提成合计 */
    private BigDecimal totalAmount;

    public MonthlySummary() {
        this.normalHoursTotal = BigDecimal.ZERO;
        this.overtimeHoursTotal = BigDecimal.ZERO;
        this.byCountAmount = BigDecimal.ZERO;
        this.byHourAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * 空汇总（对应 TS 的 emptySummary）。
     */
    public static MonthlySummary empty() {
        return new MonthlySummary();
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public int getCountTotal() {
        return countTotal;
    }

    public void setCountTotal(int countTotal) {
        this.countTotal = countTotal;
    }

    public BigDecimal getNormalHoursTotal() {
        return normalHoursTotal;
    }

    public void setNormalHoursTotal(BigDecimal normalHoursTotal) {
        this.normalHoursTotal = normalHoursTotal;
    }

    public BigDecimal getOvertimeHoursTotal() {
        return overtimeHoursTotal;
    }

    public void setOvertimeHoursTotal(BigDecimal overtimeHoursTotal) {
        this.overtimeHoursTotal = overtimeHoursTotal;
    }

    public BigDecimal getByCountAmount() {
        return byCountAmount;
    }

    public void setByCountAmount(BigDecimal byCountAmount) {
        this.byCountAmount = byCountAmount;
    }

    public BigDecimal getByHourAmount() {
        return byHourAmount;
    }

    public void setByHourAmount(BigDecimal byHourAmount) {
        this.byHourAmount = byHourAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "MonthlySummary{"
                + "recordCount=" + recordCount
                + ", countTotal=" + countTotal
                + ", normalHoursTotal=" + normalHoursTotal
                + ", overtimeHoursTotal=" + overtimeHoursTotal
                + ", byCountAmount=" + byCountAmount
                + ", byHourAmount=" + byHourAmount
                + ", totalAmount=" + totalAmount
                + '}';
    }
}
