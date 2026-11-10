package com.ruoyi.smtchs.dto;

import java.time.LocalDate;

/**
 * 已确认记录的修正入参（AUD-04）。
 *
 * <p>字段为 null 表示「不修改该字段」，因此不能用于把字段清空。
 * 修正说明 {@code note} 必填，用于留痕。</p>
 *
 * @author ruoyi
 */
public class RevisionDTO
{
    /** 服务日期，null 表示不修改 */
    private LocalDate serviceDate;

    /** 开始时间 HH:mm，null 表示不修改 */
    private String startTime;

    /** 结束时间 HH:mm，null 表示不修改 */
    private String endTime;

    /** 服务地点，null 表示不修改 */
    private String location;

    /** 工作内容，null 表示不修改 */
    private String content;

    /** 修正说明（必填，≤200 字符） */
    private String note;

    public LocalDate getServiceDate()
    {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate)
    {
        this.serviceDate = serviceDate;
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

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    @Override
    public String toString()
    {
        return "RevisionDTO{"
                + "serviceDate=" + serviceDate
                + ", startTime='" + startTime + '\''
                + ", endTime='" + endTime + '\''
                + ", location='" + location + '\''
                + ", content='" + content + '\''
                + ", note='" + note + '\''
                + '}';
    }
}
