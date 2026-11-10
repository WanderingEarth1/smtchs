package com.ruoyi.smtchs.dto;

import com.ruoyi.smtchs.billing.MonthlySummary;

/**
 * 按工作类型维度的月度汇总。
 *
 * @author ruoyi
 */
public class WorkTypeSummaryVO
{
    /** 工作类型ID */
    private Long workTypeId;

    /** 工作类型名称 */
    private String workTypeName;

    /** 计费模式（BY_COUNT / BY_HOUR） */
    private String billingMode;

    /** 该工作类型的月度汇总 */
    private MonthlySummary summary;

    public WorkTypeSummaryVO()
    {
        this.summary = MonthlySummary.empty();
    }

    public WorkTypeSummaryVO(Long workTypeId, String workTypeName, String billingMode,
                             MonthlySummary summary)
    {
        this.workTypeId = workTypeId;
        this.workTypeName = workTypeName;
        this.billingMode = billingMode;
        this.summary = summary == null ? MonthlySummary.empty() : summary;
    }

    public Long getWorkTypeId()
    {
        return workTypeId;
    }

    public void setWorkTypeId(Long workTypeId)
    {
        this.workTypeId = workTypeId;
    }

    public String getWorkTypeName()
    {
        return workTypeName;
    }

    public void setWorkTypeName(String workTypeName)
    {
        this.workTypeName = workTypeName;
    }

    public String getBillingMode()
    {
        return billingMode;
    }

    public void setBillingMode(String billingMode)
    {
        this.billingMode = billingMode;
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
        return "WorkTypeSummaryVO{"
                + "workTypeId=" + workTypeId
                + ", workTypeName='" + workTypeName + '\''
                + ", billingMode='" + billingMode + '\''
                + ", summary=" + summary
                + '}';
    }
}
