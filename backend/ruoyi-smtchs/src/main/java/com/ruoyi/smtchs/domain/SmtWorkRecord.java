package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 服务记录对象 smt_work_record
 *
 * <p>核心业务表，除常规字段外还保存提交时刻的计费参数快照（5 个 snap_* 字段），
 * 保证历史记录在计费配置变更后仍可复算。</p>
 *
 * @author ruoyi
 */
public class SmtWorkRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 流水号 */
    private String serialNo;

    /** 员工ID */
    private Long employeeId;

    /** 工作类型ID */
    private Long workTypeId;

    /** 服务日期 */
    private LocalDate serviceDate;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm，按次为空 */
    private String endTime;

    /** 服务地点 */
    private String location;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 是否获取定位（1是 0否） */
    private Integer geoCaptured;

    /** 工作内容 */
    private String content;

    // 备注字段 remark 由 BaseEntity 提供，此处不再重复声明

    /** 状态（PENDING待确认 APPROVED已确认 REJECTED已驳回） */
    private String status;

    /** 是否补录（1是 0否） */
    private Integer isBackfill;

    /** 驳回原因 */
    private String rejectReason;

    /** 提交时间 */
    private Date submittedAt;

    /** 确认时间 */
    private Date confirmedAt;

    /** 驳回时间 */
    private Date rejectedAt;

    /** 计费模式快照（BY_COUNT/BY_HOUR） */
    private String snapBillingMode;

    /** 单价快照 */
    private BigDecimal snapUnitPrice;

    /** 加班起算时间快照 HH:mm */
    private String snapOvertimeStart;

    /** 加班倍率快照 */
    private BigDecimal snapOvertimeMultiplier;

    /** 取整规则快照（EXACT/HALF_HOUR_UP/HOUR_UP） */
    private String snapRoundingRule;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 关联照片列表（非数据库列） */
    private List<SmtRecordPhoto> photos;

    /** 员工姓名（非数据库列，列表展示用） */
    private String employeeName;

    /** 工作类型名称（非数据库列，列表展示用） */
    private String workTypeName;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getSerialNo()
    {
        return serialNo;
    }

    public void setSerialNo(String serialNo)
    {
        this.serialNo = serialNo;
    }

    public Long getEmployeeId()
    {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId)
    {
        this.employeeId = employeeId;
    }

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

    public Integer getGeoCaptured()
    {
        return geoCaptured;
    }

    public void setGeoCaptured(Integer geoCaptured)
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getIsBackfill()
    {
        return isBackfill;
    }

    public void setIsBackfill(Integer isBackfill)
    {
        this.isBackfill = isBackfill;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getConfirmedAt()
    {
        return confirmedAt;
    }

    public void setConfirmedAt(Date confirmedAt)
    {
        this.confirmedAt = confirmedAt;
    }

    public Date getRejectedAt()
    {
        return rejectedAt;
    }

    public void setRejectedAt(Date rejectedAt)
    {
        this.rejectedAt = rejectedAt;
    }

    public String getSnapBillingMode()
    {
        return snapBillingMode;
    }

    public void setSnapBillingMode(String snapBillingMode)
    {
        this.snapBillingMode = snapBillingMode;
    }

    public BigDecimal getSnapUnitPrice()
    {
        return snapUnitPrice;
    }

    public void setSnapUnitPrice(BigDecimal snapUnitPrice)
    {
        this.snapUnitPrice = snapUnitPrice;
    }

    public String getSnapOvertimeStart()
    {
        return snapOvertimeStart;
    }

    public void setSnapOvertimeStart(String snapOvertimeStart)
    {
        this.snapOvertimeStart = snapOvertimeStart;
    }

    public BigDecimal getSnapOvertimeMultiplier()
    {
        return snapOvertimeMultiplier;
    }

    public void setSnapOvertimeMultiplier(BigDecimal snapOvertimeMultiplier)
    {
        this.snapOvertimeMultiplier = snapOvertimeMultiplier;
    }

    public String getSnapRoundingRule()
    {
        return snapRoundingRule;
    }

    public void setSnapRoundingRule(String snapRoundingRule)
    {
        this.snapRoundingRule = snapRoundingRule;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public List<SmtRecordPhoto> getPhotos()
    {
        return photos;
    }

    public void setPhotos(List<SmtRecordPhoto> photos)
    {
        this.photos = photos;
    }

    public String getEmployeeName()
    {
        return employeeName;
    }

    public void setEmployeeName(String employeeName)
    {
        this.employeeName = employeeName;
    }

    public String getWorkTypeName()
    {
        return workTypeName;
    }

    public void setWorkTypeName(String workTypeName)
    {
        this.workTypeName = workTypeName;
    }

    @Override
    public String toString()
    {
        return "SmtWorkRecord{"
                + "id=" + id
                + ", serialNo='" + serialNo + '\''
                + ", employeeId=" + employeeId
                + ", workTypeId=" + workTypeId
                + ", serviceDate=" + serviceDate
                + ", startTime='" + startTime + '\''
                + ", endTime='" + endTime + '\''
                + ", location='" + location + '\''
                + ", latitude=" + latitude
                + ", longitude=" + longitude
                + ", geoCaptured=" + geoCaptured
                + ", content='" + content + '\''
                + ", remark='" + getRemark() + '\''
                + ", status='" + status + '\''
                + ", isBackfill=" + isBackfill
                + ", rejectReason='" + rejectReason + '\''
                + ", submittedAt=" + submittedAt
                + ", confirmedAt=" + confirmedAt
                + ", rejectedAt=" + rejectedAt
                + ", snapBillingMode='" + snapBillingMode + '\''
                + ", snapUnitPrice=" + snapUnitPrice
                + ", snapOvertimeStart='" + snapOvertimeStart + '\''
                + ", snapOvertimeMultiplier=" + snapOvertimeMultiplier
                + ", snapRoundingRule='" + snapRoundingRule + '\''
                + ", delFlag='" + delFlag + '\''
                + '}';
    }
}
