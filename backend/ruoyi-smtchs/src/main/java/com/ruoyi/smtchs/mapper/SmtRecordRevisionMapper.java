package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtRecordRevision;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 修正痕迹 Mapper 接口。
 *
 * @author ruoyi
 */
@Mapper
public interface SmtRecordRevisionMapper
{
    /**
     * 查询修正痕迹列表。
     *
     * @param query 查询条件
     * @return 修正痕迹集合
     */
    List<SmtRecordRevision> selectRevisionList(SmtRecordRevision query);

    /**
     * 根据主键查询修正痕迹。
     *
     * @param id 主键
     * @return 修正痕迹
     */
    SmtRecordRevision selectRevisionById(Long id);

    /**
     * 根据记录ID查询修正痕迹列表（按修正时间倒序）。
     *
     * @param recordId 记录ID
     * @return 修正痕迹集合
     */
    List<SmtRecordRevision> selectRevisionByRecordId(Long recordId);

    /**
     * 新增修正痕迹。
     *
     * @param revision 修正痕迹
     * @return 影响行数
     */
    int insertRevision(SmtRecordRevision revision);

    /**
     * 根据主键删除修正痕迹。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteRevisionById(Long id);

    /**
     * 根据记录ID删除全部修正痕迹。
     *
     * @param recordId 记录ID
     * @return 影响行数
     */
    int deleteRevisionByRecordId(Long recordId);
}
