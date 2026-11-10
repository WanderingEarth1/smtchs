package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 记录照片对象 smt_record_photo
 *
 * <p>对应表 {@code smt_record_photo}，一条服务记录可关联多张照片。</p>
 *
 * @author ruoyi
 */
public class SmtRecordPhoto extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 记录ID */
    private Long recordId;

    /** 照片URL */
    private String url;

    /** 排序 */
    private Integer sort;

    /** 文件大小（字节） */
    private Integer size;

    /** 创建时间：继承自 BaseEntity 的 createTime 字段 */

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

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    public Integer getSize()
    {
        return size;
    }

    public void setSize(Integer size)
    {
        this.size = size;
    }

    @Override
    public String toString()
    {
        return "SmtRecordPhoto{"
                + "id=" + id
                + ", recordId=" + recordId
                + ", url='" + url + '\''
                + ", sort=" + sort
                + ", size=" + size
                + '}';
    }
}
