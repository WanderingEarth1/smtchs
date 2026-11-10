package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.domain.SmtPriceRule;
import com.ruoyi.smtchs.mapper.SmtPriceRuleMapper;
import com.ruoyi.smtchs.service.ISmtPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 单价矩阵服务实现（CFG-03）。
 *
 * @author ruoyi
 */
@Service
public class SmtPriceServiceImpl implements ISmtPriceService
{
    private static final Logger log = LoggerFactory.getLogger(SmtPriceServiceImpl.class);

    /** 单价上限 */
    private static final BigDecimal PRICE_MAX = new BigDecimal("100000");

    /** 金额保留位数 */
    private static final int PRICE_SCALE = 2;

    @Autowired
    private SmtPriceRuleMapper priceRuleMapper;

    @Override
    public List<SmtPriceRule> selectPriceRuleList(SmtPriceRule query)
    {
        return priceRuleMapper.selectPriceRuleList(query == null ? new SmtPriceRule() : query);
    }

    @Override
    public List<SmtPriceRule> selectMatrix()
    {
        return priceRuleMapper.selectMatrix();
    }

    @Override
    public BigDecimal selectPrice(Long employeeId, Long workTypeId)
    {
        if (employeeId == null || workTypeId == null)
        {
            return null;
        }
        return priceRuleMapper.selectPrice(employeeId, workTypeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtPriceRule setUnitPrice(Long employeeId, Long workTypeId, BigDecimal price)
    {
        if (employeeId == null)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (workTypeId == null)
        {
            throw new ServiceException("工作类型ID不能为空");
        }
        if (price == null)
        {
            throw new ServiceException("请填写单价");
        }
        if (price.signum() < 0)
        {
            throw new ServiceException("单价必须为非负数");
        }
        if (price.compareTo(PRICE_MAX) > 0)
        {
            throw new ServiceException("单价不能超过 100000");
        }

        BigDecimal normalized = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        SmtPriceRule rule = new SmtPriceRule();
        rule.setEmployeeId(employeeId);
        rule.setWorkTypeId(workTypeId);
        rule.setPrice(normalized);
        rule.setEffectiveAt(new Date());
        rule.setCreateBy(SecurityUtils.getUsername());
        rule.setUpdateBy(SecurityUtils.getUsername());

        // 依赖唯一键 uk_emp_type(employee_id, work_type_id) 实现「存在即更新」
        priceRuleMapper.upsertPrice(rule);
        log.info("设置单价 employeeId={}，workTypeId={}，price={}",
                employeeId, workTypeId, normalized);

        // upsert 在更新分支不会回填 id，故按业务键重新查询
        SmtPriceRule query = new SmtPriceRule();
        query.setEmployeeId(employeeId);
        query.setWorkTypeId(workTypeId);
        List<SmtPriceRule> saved = priceRuleMapper.selectPriceRuleList(query);
        return (saved == null || saved.isEmpty()) ? rule : saved.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int copyPriceRow(Long fromEmployeeId, List<Long> toEmployeeIds)
    {
        if (fromEmployeeId == null)
        {
            throw new ServiceException("源员工ID不能为空");
        }
        if (toEmployeeIds == null || toEmployeeIds.isEmpty())
        {
            throw new ServiceException("请选择要复制到的员工");
        }

        SmtPriceRule sourceQuery = new SmtPriceRule();
        sourceQuery.setEmployeeId(fromEmployeeId);
        List<SmtPriceRule> source = priceRuleMapper.selectPriceRuleList(sourceQuery);
        if (source == null || source.isEmpty())
        {
            throw new ServiceException("该员工尚未配置任何单价");
        }

        String operator = SecurityUtils.getUsername();
        Date now = new Date();
        int count = 0;

        for (Long toId : toEmployeeIds)
        {
            if (toId == null || toId.equals(fromEmployeeId))
            {
                continue;
            }
            for (SmtPriceRule src : source)
            {
                SmtPriceRule rule = new SmtPriceRule();
                rule.setEmployeeId(toId);
                rule.setWorkTypeId(src.getWorkTypeId());
                rule.setPrice(src.getPrice());
                rule.setEffectiveAt(now);
                rule.setCreateBy(operator);
                rule.setUpdateBy(operator);
                priceRuleMapper.upsertPrice(rule);
                count++;
            }
        }

        log.info("复制单价：源员工 {} → {} 名员工，共写入 {} 条",
                fromEmployeeId, toEmployeeIds.size(), count);
        return count;
    }
}
