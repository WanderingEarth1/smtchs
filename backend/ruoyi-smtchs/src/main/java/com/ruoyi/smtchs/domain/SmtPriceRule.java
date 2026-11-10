package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 单价矩阵对象 smt_price_rule
 *
 * <p>对应表 {@code smt_price_rule}，维护「员工 × 工作类型」的单价与生效时间。
 * 同一员工与工作类型组合唯一（uk_emp_type）。</p>
 *
 * @author ruoyi
 */
public class SmtPriceRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 工作类型ID */
    private Long workTypeId;

    /** 单价（元/次 或 元/小时） */
    private BigDecimal price;

    /** 生效时间 */
    private Date effectiveAt;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 员工姓名（非数据库列，矩阵展示用） */
    private String employeeName;

    /** 工作类型名称（非数据库列，矩阵展示用） */
    private String workTypeName;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public Date getEffectiveAt()
    {
        return effectiveAt;
    }

    public void setEffectiveAt(Date effectiveAt)
    {
        this.effectiveAt = effectiveAt;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
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
        return "SmtPriceRule{"
                + "id=" + id
                + ", employeeId=" + employeeId
                + ", workTypeId=" + workTypeId
                + ", price=" + price
                + ", effectiveAt=" + effectiveAt
                + ", delFlag='" + delFlag + '\''
                + '}';
    }
}
