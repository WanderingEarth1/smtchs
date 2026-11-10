package com.ruoyi.smtchs.dto;

import com.ruoyi.smtchs.billing.MonthlySummary;

/**
 * 按员工维度的月度汇总。
 *
 * @author ruoyi
 */
public class EmployeeSummaryVO
{
    /** 员工ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 该员工的月度汇总 */
    private MonthlySummary summary;

    public EmployeeSummaryVO()
    {
        this.summary = MonthlySummary.empty();
    }

    public EmployeeSummaryVO(Long employeeId, String employeeName, MonthlySummary summary)
    {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.summary = summary == null ? MonthlySummary.empty() : summary;
    }

    public Long getEmployeeId()
    {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId)
    {
        this.employeeId = employeeId;
    }

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public MonthlySummary getSummary()
    {
        return summary;
    }

    public void setSummary(MonthlySummary summary)
    {
        this.summary = summary;
    }

    @Override
    public String toString()
    {
        return "EmployeeSummaryVO{"
                + "employeeId=" + employeeId
                + ", employeeName='" + employeeName + '\''
                + ", summary=" + summary
                + '}';
    }
}
