package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 计费配置对象 smt_sys_config
 *
 * <p>对应表 {@code smt_sys_config}，全局唯一一行，保存加班起算时间、加班倍率与取整规则。</p>
 *
 * @author ruoyi
 */
public class SmtSysConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 加班起算时间 HH:mm */
    private String overtimeStart;

    /** 加班倍率 */
    private BigDecimal overtimeMultiplier;

    /** 取整规则（EXACT/HALF_HOUR_UP/HOUR_UP），取值见 {@link com.ruoyi.smtchs.billing.RoundingRule} */
    private String roundingRule;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getOvertimeStart()
    {
        return overtimeStart;
    }

    public void setOvertimeStart(String overtimeStart)
    {
        this.overtimeStart = overtimeStart;
    }

    public BigDecimal getOvertimeMultiplier()
    {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier)
    {
        this.overtimeMultiplier = overtimeMultiplier;
    }

    public String getRoundingRule()
    {
        return roundingRule;
    }

    public void setRoundingRule(String roundingRule)
    {
        this.roundingRule = roundingRule;
    }

    @Override
    public String toString()
    {
        return "SmtSysConfig{"
                + "id=" + id
                + ", overtimeStart='" + overtimeStart + '\''
                + ", overtimeMultiplier=" + overtimeMultiplier
                + ", roundingRule='" + roundingRule + '\''
                + '}';
    }
}
