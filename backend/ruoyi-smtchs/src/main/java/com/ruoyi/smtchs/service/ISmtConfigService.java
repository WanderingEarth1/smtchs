package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.domain.SmtSysConfig;

/**
 * 计费配置服务接口（CFG-04）。
 *
 * <p>该表全局仅一行，保存加班起算时间、加班倍率与取整规则。</p>
 *
 * @author ruoyi
 */
public interface ISmtConfigService
{
    /**
     * 查询计费配置。
     *
     * <p>未初始化时返回默认配置（加班起算 18:00、倍率 2、精确取整），
     * 保证调用方无需处理 null。</p>
     *
     * @return 计费配置
     */
    SmtSysConfig getConfig();

    /**
     * 更新计费配置。
     *
     * <p>校验：加班起算时间须为 HH:mm；加班倍率须在 [1, 10] 区间；
     * 取整规则须为 EXACT / HALF_HOUR_UP / HOUR_UP 之一。</p>
     *
     * @param config 计费配置
     * @return 更新后的计费配置
     */
    SmtSysConfig updateConfig(SmtSysConfig config);
}
