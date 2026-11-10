package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

/**
 * 启动程序
 *
 * <p>{@code @EnableGlobalMethodSecurity(prePostEnabled = true)} 让
 * {@code @PreAuthorize("@ss.hasPermi('smtchs:xxx:yyy')")} 生效，
 * 这是若依 3.8.7（Spring Boot 2.5 + Spring Security 5.5）的用法。
 * 升级到 Spring Boot 3 时应改为 {@code @EnableMethodSecurity}。</p>
 *
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  若依启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
