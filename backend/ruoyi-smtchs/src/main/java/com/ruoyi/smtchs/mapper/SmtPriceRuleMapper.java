package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtPriceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单价矩阵 Mapper 接口。
 *
 * @author ruoyi
 */
@Mapper
public interface SmtPriceRuleMapper
{
    /**
     * 查询单价矩阵列表。
     *
     * @param query 查询条件
     * @return 单价规则集合
     */
    List<SmtPriceRule> selectPriceRuleList(SmtPriceRule query);

    /**
     * 根据主键查询单价规则。
     *
     * @param id 主键
     * @return 单价规则
     */
    SmtPriceRule selectPriceRuleById(Long id);

    /**
     * 新增单价规则。
     *
     * @param rule 单价规则
     * @return 影响行数
     */
    int insertPriceRule(SmtPriceRule rule);

    /**
     * 修改单价规则。
     *
     * @param rule 单价规则
     * @return 影响行数
     */
    int updatePriceRule(SmtPriceRule rule);

    /**
     * 根据主键逻辑删除单价规则。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deletePriceRuleById(Long id);

    /**
     * 查询指定员工与工作类型的当前生效单价。
     *
     * @param employeeId 员工ID
     * @param workTypeId 工作类型ID
     * @return 单价，未配置时返回 null
     */
    BigDecimal selectPrice(@Param("employeeId") Long employeeId,
                           @Param("workTypeId") Long workTypeId);

    /**
     * 查询完整单价矩阵（员工 × 工作类型，含姓名与类型名称）。
     *
     * @return 单价规则集合
     */
    List<SmtPriceRule> selectMatrix();

    /**
     * 新增或更新单价（按 uk_emp_type 唯一键冲突时更新）。
     *
     * @param rule 单价规则
     * @return 影响行数
     */
    int upsertPrice(SmtPriceRule rule);
}
