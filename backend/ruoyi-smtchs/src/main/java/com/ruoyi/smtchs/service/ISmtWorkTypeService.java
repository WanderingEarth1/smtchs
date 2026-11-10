package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.domain.SmtWorkType;

import java.util.List;

/**
 * 工作类型服务接口（CFG-02）。
 *
 * @author ruoyi
 */
public interface ISmtWorkTypeService
{
    /**
     * 查询工作类型列表。
     *
     * @param query 查询条件
     * @return 工作类型集合
     */
    List<SmtWorkType> selectWorkTypeList(SmtWorkType query);

    /**
     * 查询全部启用中的工作类型（下拉选项用）。
     *
     * @return 工作类型集合
     */
    List<SmtWorkType> selectEnabledWorkTypes();

    /**
     * 根据主键查询工作类型。
     *
     * @param id 主键
     * @return 工作类型
     */
    SmtWorkType selectWorkTypeById(Long id);

    /**
     * 新增工作类型。
     *
     * @param workType 工作类型
     * @return 新增后的工作类型
     */
    SmtWorkType createWorkType(SmtWorkType workType);

    /**
     * 修改工作类型。
     *
     * @param workType 工作类型
     * @return 修改后的工作类型
     */
    SmtWorkType updateWorkType(SmtWorkType workType);

    /**
     * 删除工作类型。
     *
     * <p>已被服务记录引用时改为停用，避免历史记录失去类型名称。</p>
     *
     * @param id 工作类型ID
     * @return true 表示因被引用而改为停用，false 表示已逻辑删除
     */
    boolean deleteWorkType(Long id);
}
