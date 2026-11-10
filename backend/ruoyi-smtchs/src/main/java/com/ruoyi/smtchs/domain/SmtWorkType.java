package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 工作类型对象 smt_work_type
 *
 * <p>对应表 {@code smt_work_type}，区分按次（BY_COUNT）与按小时（BY_HOUR）计费。</p>
 *
 * @author ruoyi
 */
public class SmtWorkType extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 类型名称 */
    private String name;

    /** 计费模式（BY_COUNT按次 BY_HOUR按小时），取值见 {@link com.ruoyi.smtchs.billing.BillingMode} */
    private String billingMode;

    /** 是否启用（1启用 0停用） */
    private Integer enabled;

    /** 排序 */
    private Integer sort;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getBillingMode()
    {
        return billingMode;
    }

    public void setBillingMode(String billingMode)
    {
        this.billingMode = billingMode;
    }

    public Integer getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Integer enabled)
    {
        this.enabled = enabled;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return "SmtWorkType{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", billingMode='" + billingMode + '\''
                + ", enabled=" + enabled
                + ", sort=" + sort
                + ", delFlag='" + delFlag + '\''
                + '}';
    }
}
