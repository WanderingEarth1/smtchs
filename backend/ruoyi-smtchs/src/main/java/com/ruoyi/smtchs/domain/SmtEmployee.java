package com.ruoyi.smtchs.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 员工档案对象 smt_employee
 *
 * <p>对应表 {@code smt_employee}，用于员工登录、状态与锁定信息维护。</p>
 *
 * @author ruoyi
 */
public class SmtEmployee extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 sys_user.user_id */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 登录账号 */
    private String account;

    /** 状态（0启用 1停用） */
    private String status;

    /** 连续登录失败次数 */
    private Integer failedAttempts;

    /** 锁定截止时间 */
    private Date lockedUntil;

    /**
     * 密码密文（非 smt_employee 列，来自关联的 sys_user.password）。
     *
     * <p>仅在登录校验与改密场景由 {@code selectByAccount} 关联查出，
     * 列表/详情查询不会返回该字段，避免密文外泄。</p>
     */
    private String password;

    /**
     * 角色（EMPLOYEE 员工 / ADMIN 管理员）。
     *
     * <p>非 smt_employee 表列，由 {@code selectByAccount} 关联 sys_user 的
     * 角色标识推导：拥有 admin 角色即为 ADMIN，否则为 EMPLOYEE。</p>
     */
    private String role;

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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getAccount()
    {
        return account;
    }

    public void setAccount(String account)
    {
        this.account = account;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getFailedAttempts()
    {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts)
    {
        this.failedAttempts = failedAttempts;
    }

    public Date getLockedUntil()
    {
        return lockedUntil;
    }

    public void setLockedUntil(Date lockedUntil)
    {
        this.lockedUntil = lockedUntil;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
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
        return "SmtEmployee{"
                + "id=" + id
                + ", userId=" + userId
                + ", name='" + name + '\''
                + ", account='" + account + '\''
                + ", status='" + status + '\''
                + ", failedAttempts=" + failedAttempts
                + ", lockedUntil=" + lockedUntil
                + ", delFlag='" + delFlag + '\''
                + '}';
    }
}
