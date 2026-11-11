package com.ruoyi.web.controller.smtchs;


import java.math.BigDecimal;

/**
 * 月度报表「明细表」导出行对象。
 *
 * <p>对应 Excel 第 1 个 Sheet，共 17 列。字段全部为展示用字符串或数值，
 * 由 {@link SmtReportController} 从 {@code SmtWorkRecord} 转换而来，
 * 金额与时长由计费引擎依据记录内的 {@code snap_*} 快照复算。</p>
 *
 * @author ruoyi
 */
public class RecordDetailExcelVO
{
    /** 流水号 */
    private String serialNo;

    /** 员工姓名 */
    private String employeeName;

    /** 服务日期 yyyy-MM-dd */
    private String serviceDate;

    /** 工作类型名称 */
    private String workTypeName;

    /** 计费模式中文（按小时 / 按次） */
    private String billingMode;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm，按次为「—」 */
    private String endTime;

    /** 正常时长（小时） */
    private BigDecimal normalHours;

    /** 加班时长（小时） */
    private BigDecimal overtimeHours;

    /** 服务地点 */
    private String location;

    /** 定位信息（纬度,经度）或「未获取定位」 */
    private String geoText;

    /** 工作内容摘要（截断至 60 字） */
    private String contentSummary;

    /** 照片链接说明（凭证 N 张 / 无） */
    private String photoLinks;

    /** 状态中文（待确认 / 已确认 / 已驳回） */
    private String statusText;

    /** 是否补录（是 / 否） */
    private String backfillText;

    /** 单价（元/次 或 元/小时） */
    private BigDecimal unitPrice;

    /** 提成金额（元） */
    private BigDecimal amount;

    public String getSerialNo()
    {
        return serialNo;
    }

    public void setSerialNo(String serialNo)
    {
        this.serialNo = serialNo;
    }

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public String getServiceDate()
    {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate)
    {
        this.serviceDate = serviceDate;
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

    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public BigDecimal getNormalHours()
    {
        return normalHours;
    }

    public void setNormalHours(BigDecimal normalHours)
    {
        this.normalHours = normalHours;
    }

    public BigDecimal getOvertimeHours()
    {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours)
    {
        this.overtimeHours = overtimeHours;
    }

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
    }

    public String getGeoText()
    {
        return geoText;
    }

    public void setGeoText(String geoText)
    {
        this.geoText = geoText;
    }

    public String getContentSummary()
    {
        return contentSummary;
    }

    public void setContentSummary(String contentSummary)
    {
        this.contentSummary = contentSummary;
    }

    public String getPhotoLinks()
    {
        return photoLinks;
    }

    public void setPhotoLinks(String photoLinks)
    {
        this.photoLinks = photoLinks;
    }

    public String getStatusText()
    {
        return statusText;
    }

    public void setStatusText(String statusText)
    {
        this.statusText = statusText;
    }

    public String getBackfillText()
    {
        return backfillText;
    }

    public void setBackfillText(String backfillText)
    {
        this.backfillText = backfillText;
    }

    public BigDecimal getUnitPrice()
    {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice)
    {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }
}
