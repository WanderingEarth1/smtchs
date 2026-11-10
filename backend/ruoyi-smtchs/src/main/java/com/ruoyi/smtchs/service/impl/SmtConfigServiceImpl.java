package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.billing.RoundingRule;
import com.ruoyi.smtchs.domain.SmtSysConfig;
import com.ruoyi.smtchs.mapper.SmtSysConfigMapper;
import com.ruoyi.smtchs.service.ISmtConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 计费配置服务实现（CFG-04）。
 *
 * @author ruoyi
 */
@Service
public class SmtConfigServiceImpl implements ISmtConfigService
{
    private static final Logger log = LoggerFactory.getLogger(SmtConfigServiceImpl.class);

    /** HH:mm 格式校验 */
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /** 加班倍率下限 */
    private static final BigDecimal MULTIPLIER_MIN = BigDecimal.ONE;

    /** 加班倍率上限 */
    private static final BigDecimal MULTIPLIER_MAX = BigDecimal.TEN;

    /** 默认加班起算时间 */
    private static final String DEFAULT_OVERTIME_START = "18:00";

    /** 默认加班倍率 */
    private static final BigDecimal DEFAULT_OVERTIME_MULTIPLIER = new BigDecimal("2.00");

    /** 默认取整规则 */
    private static final String DEFAULT_ROUNDING_RULE = RoundingRule.EXACT.name();

    @Autowired
    private SmtSysConfigMapper configMapper;

    @Override
    public SmtSysConfig getConfig()
    {
        SmtSysConfig config = configMapper.selectConfig();
        if (config == null)
        {
            // 未初始化时返回默认值，保证调用方无需处理 null
            config = new SmtSysConfig();
            config.setOvertimeStart(DEFAULT_OVERTIME_START);
            config.setOvertimeMultiplier(DEFAULT_OVERTIME_MULTIPLIER);
            config.setRoundingRule(DEFAULT_ROUNDING_RULE);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtSysConfig updateConfig(SmtSysConfig config)
    {
        if (config == null)
        {
            throw new ServiceException("计费配置不能为空");
        }

        String overtimeStart = trimToNull(config.getOvertimeStart());
        if (overtimeStart == null)
        {
            throw new ServiceException("请填写加班起算时间");
        }
        if (!TIME_PATTERN.matcher(overtimeStart).matches())
        {
            throw new ServiceException("加班起算时间格式应为 HH:mm");
        }

        BigDecimal multiplier = config.getOvertimeMultiplier();
        if (multiplier == null)
        {
            throw new ServiceException("请填写加班倍率");
        }
        if (multiplier.compareTo(MULTIPLIER_MIN) < 0)
        {
            throw new ServiceException("加班倍率必须不小于 1");
        }
        if (multiplier.compareTo(MULTIPLIER_MAX) > 0)
        {
            throw new ServiceException("加班倍率不能超过 10");
        }

        String roundingRule = trimToNull(config.getRoundingRule());
        if (roundingRule == null)
        {
            throw new ServiceException("请选择取整规则");
        }
        if (!isValidRoundingRule(roundingRule))
        {
            throw new ServiceException("取整规则取值非法");
        }

        config.setOvertimeStart(overtimeStart);
        config.setRoundingRule(roundingRule);
        config.setUpdateBy(SecurityUtils.getUsername());

        SmtSysConfig current = configMapper.selectConfig();
        if (current == null)
        {
            configMapper.insertConfig(config);
            log.info("初始化计费配置：加班起算 {}，倍率 {}，取整 {}",
                    overtimeStart, multiplier, roundingRule);
        }
        else
        {
            config.setId(current.getId());
            configMapper.updateConfig(config);
            log.info("更新计费配置 id={}：加班起算 {}，倍率 {}，取整 {}",
                    current.getId(), overtimeStart, multiplier, roundingRule);
        }

        return configMapper.selectConfig();
    }

    /**
     * 校验取整规则取值是否合法。
     */
    private boolean isValidRoundingRule(String rule)
    {
        for (RoundingRule item : RoundingRule.values())
        {
            if (item.name().equals(rule))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除首尾空白，空串归一为 null。
     */
    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
