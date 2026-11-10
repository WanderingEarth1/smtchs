package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtSysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费配置 Mapper 接口。
 *
 * <p>该表全局仅一行，故不提供列表与删除方法。</p>
 *
 * @author ruoyi
 */
@Mapper
public interface SmtSysConfigMapper
{
    /**
     * 查询计费配置（全局唯一一行）。
     *
     * @return 计费配置，未初始化时返回 null
     */
    SmtSysConfig selectConfig();

    /**
     * 根据主键查询计费配置。
     *
     * @param id 主键
     * @return 计费配置
     */
    SmtSysConfig selectConfigById(Long id);

    /**
     * 新增计费配置。
     *
     * @param config 计费配置
     * @return 影响行数
     */
    int insertConfig(SmtSysConfig config);

    /**
     * 修改计费配置。
     *
     * @param config 计费配置
     * @return 影响行数
     */
    int updateConfig(SmtSysConfig config);
}
