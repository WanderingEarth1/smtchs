package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 修正痕迹对象 smt_record_revision
 *
 * <p>对应表 {@code smt_record_revision}，保存服务记录被修改时的前后快照与说明。</p>
 *
 * @author ruoyi
 */
public class SmtRecordRevision extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 记录ID */
    private Long recordId;

    /** 修正人姓名 */
    private String operatorName;

    /** 修正时间 */
    private Date revisedAt;

    /** 修正前快照JSON */
    private String beforeJson;

    /** 修正后快照JSON */
    private String afterJson;

    /** 修正说明 */
    private String note;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public Date getRevisedAt()
    {
        return revisedAt;
    }

    public void setRevisedAt(Date revisedAt)
    {
        this.revisedAt = revisedAt;
    }

    public String getBeforeJson()
    {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson)
    {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson()
    {
        return afterJson;
    }

    public void setAfterJson(String afterJson)
    {
        this.afterJson = afterJson;
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
        return "SmtRecordRevision{"
                + "id=" + id
                + ", recordId=" + recordId
                + ", operatorName='" + operatorName + '\''
                + ", revisedAt=" + revisedAt
                + ", note='" + note + '\''
                + '}';
    }
}
