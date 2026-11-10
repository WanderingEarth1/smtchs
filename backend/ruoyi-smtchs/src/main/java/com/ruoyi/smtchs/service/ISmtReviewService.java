package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.domain.SmtRecordRevision;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.RevisionDTO;

import java.util.List;
import java.util.Map;

/**
 * 审核服务接口（AUD-01 ~ AUD-05）。
 *
 * <p>确认与驳回均使用「仅状态仍为 PENDING 才更新」的乐观锁，
 * 避免多人同时审核同一记录时产生覆盖。</p>
 *
 * @author ruoyi
 */
public interface ISmtReviewService
{
    /**
     * 确认单条记录（AUD-02）。
     *
     * @param recordId 记录ID
     */
    void approveRecord(Long recordId);

    /**
     * 批量确认（AUD-03）。
     *
     * <p>逐条独立判定，已在其它端被处理的记录计入失败数而不中断整批。</p>
     *
     * @param recordIds 记录ID集合
     * @return 含 {@code success} 与 {@code failed} 两个键的结果
     */
    Map<String, Integer> approveRecords(List<Long> recordIds);

    /**
     * 驳回记录（AUD-02：驳回原因必填）。
     *
     * @param recordId 记录ID
     * @param reason   驳回原因，≤200 字符
     */
    void rejectRecord(Long recordId, String reason);

    /**
     * 修正已确认记录（AUD-04：留痕）。
     *
     * <p>保存修正前后快照到 {@code smt_record_revision}，记录状态保持 APPROVED。</p>
     *
     * @param recordId 记录ID
     * @param dto      修正内容与说明
     * @return 修正后的服务记录
     */
    SmtWorkRecord reviseRecord(Long recordId, RevisionDTO dto);

    /**
     * 查询指定记录的修正痕迹（按修正时间倒序）。
     *
     * @param recordId 记录ID
     * @return 修正痕迹集合
     */
    List<SmtRecordRevision> selectRevisions(Long recordId);
}
