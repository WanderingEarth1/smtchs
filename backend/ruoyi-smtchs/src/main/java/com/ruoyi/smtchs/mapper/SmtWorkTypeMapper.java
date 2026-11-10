package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtWorkType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工作类型 Mapper 接口。
 *
 * @author ruoyi
 */
@Mapper
public interface SmtWorkTypeMapper
{
    /**
     * 查询工作类型列表。
     *
     * @param query 查询条件
     * @return 工作类型集合
     */
    List<SmtWorkType> selectWorkTypeList(SmtWorkType query);

    /**
     * 根据主键查询工作类型。
     *
     * @param id 主键
     * @return 工作类型
     */
    SmtWorkType selectWorkTypeById(Long id);

    /**
     * 查询全部启用中的工作类型（下拉选项用）。
     *
     * @return 工作类型集合
     */
    List<SmtWorkType> selectEnabledWorkTypes();

    /**
     * 新增工作类型。
     *
     * @param workType 工作类型
     * @return 影响行数
     */
    int insertWorkType(SmtWorkType workType);

    /**
     * 修改工作类型。
     *
     * @param workType 工作类型
     * @return 影响行数
     */
    int updateWorkType(SmtWorkType workType);

    /**
     * 根据主键逻辑删除工作类型。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteWorkTypeById(Long id);
}
