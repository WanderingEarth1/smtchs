package com.ruoyi.smtchs.dto;

import com.ruoyi.smtchs.billing.MonthlySummary;
import com.ruoyi.smtchs.domain.SmtWorkRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 月度报表响应（STA-01 / STA-02 / STA-03）。
 *
 * <p>对应前端 {@code src/data/service.ts} 的 {@code MonthlyReport}。
 * 仅「已确认」记录计入核算，待确认与已驳回只做计数提示。</p>
 *
 * @author ruoyi
 */
public class MonthlyReportVO
{
    /** 月份，格式 yyyy-MM */
    private String monthKey;

    /** 汇总视图 */
    private MonthlySummary summary;

    /** 按员工汇总（按金额降序） */
    private List<EmployeeSummaryVO> byEmployee;

    /** 按工作类型汇总（按金额降序） */
    private List<WorkTypeSummaryVO> byWorkType;

    /** 已确认记录明细（按服务日期倒序） */
    private List<SmtWorkRecord> approvedRecords;

    /** 未计入核算的待确认条数 */
    private int pendingCount;

    /** 未计入核算的已驳回条数 */
    private int rejectedCount;

    public MonthlyReportVO()
    {
        this.summary = MonthlySummary.empty();
        this.byEmployee = new ArrayList<>();
        this.byWorkType = new ArrayList<>();
        this.approvedRecords = new ArrayList<>();
    }

    public String getMonthKey()
    {
        return monthKey;
    }

    public void setMonthKey(String monthKey)
    {
        this.monthKey = monthKey;
    }

    public MonthlySummary getSummary()
    {
        return summary;
    }

    public void setSummary(MonthlySummary summary)
    {
        this.summary = summary;
    }

    public List<EmployeeSummaryVO> getByEmployee()
    {
        return byEmployee;
    }

    public void setByEmployee(List<EmployeeSummaryVO> byEmployee)
    {
        this.byEmployee = byEmployee;
    }

    public List<WorkTypeSummaryVO> getByWorkType()
    {
        return byWorkType;
    }

    public void setByWorkType(List<WorkTypeSummaryVO> byWorkType)
    {
        this.byWorkType = byWorkType;
    }

    public List<SmtWorkRecord> getApprovedRecords()
    {
        return approvedRecords;
    }

    public void setApprovedRecords(List<SmtWorkRecord> approvedRecords)
    {
        this.approvedRecords = approvedRecords;
    }

    public int getPendingCount()
    {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount)
    {
        this.pendingCount = pendingCount;
    }

    public int getRejectedCount()
    {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount)
    {
        this.rejectedCount = rejectedCount;
    }

    @Override
    public String toString()
    {
        return "MonthlyReportVO{"
                + "monthKey='" + monthKey + '\''
                + ", summary=" + summary
                + ", byEmployee=" + (byEmployee == null ? 0 : byEmployee.size()) + " 人"
                + ", byWorkType=" + (byWorkType == null ? 0 : byWorkType.size()) + " 类"
                + ", approvedRecords=" + (approvedRecords == null ? 0 : approvedRecords.size()) + " 条"
                + ", pendingCount=" + pendingCount
                + ", rejectedCount=" + rejectedCount
                + '}';
    }
}
