package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.domain.SmtPriceRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单价矩阵服务接口（CFG-03）。
 *
 * @author ruoyi
 */
public interface ISmtPriceService
{
    /**
     * 查询单价规则列表。
     *
     * @param query 查询条件
     * @return 单价规则集合
     */
    List<SmtPriceRule> selectPriceRuleList(SmtPriceRule query);

    /**
     * 查询完整单价矩阵（员工 × 工作类型，含姓名与类型名称）。
     *
     * @return 单价规则集合
     */
    List<SmtPriceRule> selectMatrix();

    /**
     * 取某员工某工作类型的当前生效单价。
     *
     * @param employeeId 员工ID
     * @param workTypeId 工作类型ID
     * @return 单价，未配置时返回 {@code null}
     */
    BigDecimal selectPrice(Long employeeId, Long workTypeId);

    /**
     * 设置单价：存在则更新，不存在则新增。
     *
     * @param employeeId 员工ID
     * @param workTypeId 工作类型ID
     * @param price      单价（元/次 或 元/小时）
     * @return 保存后的单价规则
     */
    SmtPriceRule setUnitPrice(Long employeeId, Long workTypeId, BigDecimal price);

    /**
     * 按行快速复制：把某员工的全部单价复制给其他员工。
     *
     * @param fromEmployeeId 源员工ID
     * @param toEmployeeIds  目标员工ID集合
     * @return 实际写入的单价条数
     */
    int copyPriceRow(Long fromEmployeeId, List<Long> toEmployeeIds);
}
