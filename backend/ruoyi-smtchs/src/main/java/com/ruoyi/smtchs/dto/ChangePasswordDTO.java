package com.ruoyi.smtchs.dto;

/**
 * 修改本人密码入参。
 *
 * @author ruoyi
 */
public class ChangePasswordDTO
{
    /** 原密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;

    public String getOldPassword()
    {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword)
    {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword()
    {
        return newPassword;
    }

    public void setNewPassword(String newPassword)
    {
        this.newPassword = newPassword;
    }

    /**
     * 刻意不输出密码，避免明文密码进入日志。
     */
    @Override
    public String toString()
    {
        return "ChangePasswordDTO{}";
    }
}
