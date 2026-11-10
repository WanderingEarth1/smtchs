package com.ruoyi.smtchs.dto;

/**
 * 登录入参。
 *
 * @author ruoyi
 */
public class LoginDTO
{
    /** 登录账号 */
    private String account;

    /** 登录密码（明文，仅用于本次校验，不落库） */
    private String password;

    public LoginDTO()
    {
    }

    public LoginDTO(String account, String password)
    {
        this.account = account;
        this.password = password;
    }

    public String getAccount()
    {
        return account;
    }

    public void setAccount(String account)
    {
        this.account = account;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    /**
     * 刻意不输出密码，避免明文密码进入日志。
     */
    @Override
    public String toString()
    {
        return "LoginDTO{account='" + account + "'}";
    }
}
