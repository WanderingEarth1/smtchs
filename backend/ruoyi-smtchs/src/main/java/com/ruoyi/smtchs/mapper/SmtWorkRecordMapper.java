package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtRecordPhoto;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 服务记录 Mapper 接口。
 *
 * @author ruoyi
 */
@Mapper
public interface SmtWorkRecordMapper
{
    /**
     * 查询服务记录列表（含员工姓名与工作类型名称）。
     *
     * @param query 查询条件
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectRecordList(SmtWorkRecord query);

    /**
     * 根据主键查询服务记录，同时关联查询照片列表。
     *
     * @param id 主键
     * @return 服务记录（含 photos）
     */
    SmtWorkRecord selectRecordById(Long id);

    /**
     * 根据记录ID查询照片列表（供 selectRecordById 的 collection 二次查询使用）。
     *
     * @param recordId 记录ID
     * @return 照片集合
     */
    List<SmtRecordPhoto> selectPhotosByRecordId(Long recordId);

    /**
     * 新增服务记录。
     *
     * @param record 服务记录
     * @return 影响行数
     */
    int insertRecord(SmtWorkRecord record);

    /**
     * 修改服务记录。
     *
     * @param record 服务记录
     * @return 影响行数
     */
    int updateRecord(SmtWorkRecord record);

    /**
     * 根据主键逻辑删除服务记录。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteRecordById(Long id);

    /**
     * 按月查询已确认记录（报表用）。
     *
     * @param month      月份，格式 yyyy-MM
     * @param employeeId 员工ID，可为 null
     * @param workTypeId 工作类型ID，可为 null
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectApprovedByMonth(@Param("month") String month,
                                              @Param("employeeId") Long employeeId,
                                              @Param("workTypeId") Long workTypeId);

    /**
     * 按状态统计记录数。
     *
     * @param status 状态
     * @return 记录数
     */
    int countByStatus(@Param("status") String status);

    /**
     * 查询指定前缀下的最大流水号。
     *
     * @param prefix 流水号前缀
     * @return 最大流水号，无记录时返回 null
     */
    String selectMaxSerialNo(@Param("prefix") String prefix);

    /**
     * 乐观锁更新状态：仅当记录仍为 PENDING 时才更新。
     *
     * @param id     主键
     * @param status 目标状态
     * @param time   确认/驳回时间
     * @return 影响行数，0 表示状态已被他人变更
     */
    int updateStatusIfPending(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("time") Date time);

    /**
     * 查询当前员工的服务记录（我的记录）。
     *
     * @param employeeId 员工ID
     * @param month      月份，格式 yyyy-MM，可为 null
     * @param status     状态，可为 null
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectMyRecords(@Param("employeeId") Long employeeId,
                                        @Param("month") String month,
                                        @Param("status") String status);
}
