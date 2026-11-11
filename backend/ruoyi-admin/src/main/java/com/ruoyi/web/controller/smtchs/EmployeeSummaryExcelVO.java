package com.ruoyi.web.controller.smtchs;


import java.math.BigDecimal;

/**
 * 月度报表「按人汇总表」导出行对象。
 *
 * <p>对应 Excel 第 2 个 Sheet，共 7 列。数据来源为
 * {@code MonthlyReportVO.byEmployee}（按金额降序）。</p>
 *
 * @author ruoyi
 */
public class EmployeeSummaryExcelVO
{
    /** 员工姓名 */
    private String employeeName;

    /** 按次次数合计 */
    private Integer countTotal;

    /** 正常时长合计（小时） */
    private BigDecimal normalHoursTotal;

    /** 加班时长合计（小时） */
    private BigDecimal overtimeHoursTotal;

    /** 按次金额合计（元） */
    private BigDecimal byCountAmount;

    /** 按时金额合计（元） */
    private BigDecimal byHourAmount;

    /** 提成合计（元） */
    private BigDecimal totalAmount;

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public Integer getCountTotal()
    {
        return countTotal;
    }

    public void setCountTotal(Integer countTotal)
    {
        this.countTotal = countTotal;
    }

    public BigDecimal getNormalHoursTotal()
    {
        return normalHoursTotal;
    }

    public void setNormalHoursTotal(BigDecimal normalHoursTotal)
    {
        this.normalHoursTotal = normalHoursTotal;
    }

    public BigDecimal getOvertimeHoursTotal()
    {
        return overtimeHoursTotal;
    }

    public void setOvertimeHoursTotal(BigDecimal overtimeHoursTotal)
    {
        this.overtimeHoursTotal = overtimeHoursTotal;
    }

    public BigDecimal getByCountAmount()
    {
        return byCountAmount;
    }

    public void setByCountAmount(BigDecimal byCountAmount)
    {
        this.byCountAmount = byCountAmount;
    }

    public BigDecimal getByHourAmount()
    {
        return byHourAmount;
    }

    public void setByHourAmount(BigDecimal byHourAmount)
    {
        this.byHourAmount = byHourAmount;
    }

    public BigDecimal getTotalAmount()
    {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount)
    {
        this.totalAmount = totalAmount;
    }
}
