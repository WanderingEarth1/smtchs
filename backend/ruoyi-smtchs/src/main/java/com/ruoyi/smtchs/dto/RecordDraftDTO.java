package com.ruoyi.smtchs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 服务记录草稿（新增 / 编辑入参）。
 *
 * <p>对应前端 {@code src/data/service.ts} 中的 {@code RecordDraft}。
 * 该对象只承载「员工可填写」的字段，流水号、状态、计费快照等由服务端生成。</p>
 *
 * @author ruoyi
 */
public class RecordDraftDTO
{
    /** 工作类型ID */
    private Long workTypeId;

    /** 服务日期 */
    private LocalDate serviceDate;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm，按次计费类型可为空 */
    private String endTime;

    /** 服务地点 */
    private String location;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 是否已获取定位 */
    private Boolean geoCaptured;

    /** 工作内容 */
    private String content;

    /** 备注 */
    private String remark;

    /** 照片URL列表（1~3 张） */
    private List<String> photoUrls;

    public Long getWorkTypeId()
    {
        return workTypeId;
    }

    public void setWorkTypeId(Long workTypeId)
    {
        this.workTypeId = workTypeId;
    }

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

    public BigDecimal getLatitude()
    {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude)
    {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude()
    {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude)
    {
        this.longitude = longitude;
    }

    public Boolean getGeoCaptured()
    {
        return geoCaptured;
    }

    public void setGeoCaptured(Boolean geoCaptured)
    {
        this.geoCaptured = geoCaptured;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public List<String> getPhotoUrls()
    {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls)
    {
        this.photoUrls = photoUrls;
    }

    @Override
    public String toString()
    {
        return "RecordDraftDTO{"
                + "workTypeId=" + workTypeId
                + ", serviceDate=" + serviceDate
                + ", startTime='" + startTime + '\''
                + ", endTime='" + endTime + '\''
                + ", location='" + location + '\''
                + ", latitude=" + latitude
                + ", longitude=" + longitude
                + ", geoCaptured=" + geoCaptured
                + ", content='" + content + '\''
                + ", remark='" + remark + '\''
                + ", photoUrls=" + (photoUrls == null ? 0 : photoUrls.size()) + " 张"
                + '}';
    }
}
