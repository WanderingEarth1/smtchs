package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.billing.BillingResult;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.RecordDraftDTO;

import java.util.List;

/**
 * 服务记录服务接口（REC-01 ~ REC-05）。
 *
 * @author ruoyi
 */
public interface ISmtRecordService
{
    /**
     * 新增服务记录（REC-01 / REC-02）。
     *
     * <p>组装记录时固化 5 个计费快照字段，保证历史记录在配置变更后仍可复算。</p>
     *
     * @param draft 记录草稿
     * @return 新增后的服务记录（含照片）
     */
    SmtWorkRecord createRecord(RecordDraftDTO draft);

    /**
     * 修改服务记录（REC-03）。
     *
     * <p>仅本人、且非「已确认」的记录可改；修改后重走审核（AUD-05）。</p>
     *
     * @param recordId 记录ID
     * @param draft    记录草稿
     * @return 修改后的服务记录（含照片）
     */
    SmtWorkRecord updateRecord(Long recordId, RecordDraftDTO draft);

    /**
     * 删除服务记录（REC-03）。
     *
     * <p>仅本人、且非「已确认」的记录可删。</p>
     *
     * @param recordId 记录ID
     */
    void deleteRecord(Long recordId);

    /**
     * 根据主键查询服务记录（含照片）。
     *
     * @param recordId 记录ID
     * @return 服务记录
     */
    SmtWorkRecord selectRecordById(Long recordId);

    /**
     * 查询服务记录列表（管理端）。
     *
     * @param query 查询条件
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectRecordList(SmtWorkRecord query);

    /**
     * 查询当前员工的服务记录（我的记录，REC-04）。
     *
     * @param month  月份，格式 yyyy-MM，可为 null
     * @param status 状态，可为 null
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectMyRecords(String month, String status);

    /**
     * 查询指定员工的服务记录（REC-04）。
     *
     * @param employeeId 员工ID
     * @param month      月份，格式 yyyy-MM，可为 null
     * @param status     状态，可为 null
     * @return 服务记录集合
     */
    List<SmtWorkRecord> selectMyRecords(Long employeeId, String month, String status);

    /**
     * 计算单条记录的提成金额（STA-01）。
     *
     * <p>计费入参全部取自记录内的计费快照，与当前配置无关。</p>
     *
     * @param record 服务记录
     * @return 计费结果
     */
    BillingResult calculateRecord(SmtWorkRecord record);

    /**
     * 统计待确认记录数（用于角标）。
     *
     * @return 待确认条数
     */
    int countPending();
}
