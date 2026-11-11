package com.ruoyi.smtchs.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.domain.SmtRecordRevision;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.RevisionDTO;
import com.ruoyi.smtchs.mapper.SmtRecordRevisionMapper;
import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import com.ruoyi.smtchs.service.ISmtReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核服务实现（AUD-01 ~ AUD-05）。
 *
 * <p>确认与驳回均通过「仅状态仍为 PENDING 才更新」的乐观锁语句落库，
 * 影响行数为 0 即视为状态已被他人变更。</p>
 *
 * @author ruoyi
 */
@Service
public class SmtReviewServiceImpl implements ISmtReviewService
{
    private static final Logger log = LoggerFactory.getLogger(SmtReviewServiceImpl.class);

    /** 状态：待确认 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认 */
    private static final String STATUS_APPROVED = "APPROVED";

    /** 状态：已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 驳回原因最大长度 */
    private static final int REASON_MAX_LENGTH = 200;

    /** 修正说明最大长度 */
    private static final int NOTE_MAX_LENGTH = 200;

    /** 地点最大长度 */
    private static final int LOCATION_MAX_LENGTH = 60;

    /** 工作内容最大长度 */
    private static final int CONTENT_MAX_LENGTH = 200;

    /** 修正快照序列化器：仅序列化 5 个可编辑字段，无需 JavaTimeModule */

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    @Autowired
    private SmtRecordRevisionMapper revisionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRecord(Long recordId)
    {
        if (recordId == null)
        {
            throw new ServiceException("记录ID不能为空");
        }

        SmtWorkRecord record = recordMapper.selectRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("记录不存在");
        }
        if (STATUS_APPROVED.equals(record.getStatus()))
        {
            throw new ServiceException("该记录已确认");
        }
        if (STATUS_REJECTED.equals(record.getStatus()))
        {
            throw new ServiceException("已驳回的记录需员工修改后重新提交");
        }

        // 乐观锁：只有状态仍为 PENDING 才更新
        int affected = recordMapper.updateStatusIfPending(recordId, STATUS_APPROVED, new Date());
        if (affected == 0)
        {
            throw new ServiceException("记录状态已变更，请刷新后重试");
        }

        log.info("确认服务记录 id={}，操作人={}", recordId, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> approveRecords(List<Long> recordIds)
    {
        if (recordIds == null || recordIds.isEmpty())
        {
            throw new ServiceException("请选择要确认的记录");
        }

        int success = 0;
        int failed = 0;
        Date now = new Date();

        for (Long id : recordIds)
        {
            if (id == null)
            {
                failed++;
                continue;
            }
            // 逐条独立判定：已在其它端被处理的记录计入失败数，不中断整批
            int affected = recordMapper.updateStatusIfPending(id, STATUS_APPROVED, now);
            if (affected > 0)
            {
                success++;
            }
            else
            {
                failed++;
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);

        log.info("批量确认服务记录：成功 {} 条，失败 {} 条，操作人={}",
                success, failed, SecurityUtils.getUsername());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRecord(Long recordId, String reason)
    {
        if (recordId == null)
        {
            throw new ServiceException("记录ID不能为空");
        }

        String trimmed = reason == null ? null : reason.trim();
        if (trimmed == null || trimmed.isEmpty())
        {
            throw new ServiceException("驳回时必须填写驳回原因");
        }
        if (trimmed.length() > REASON_MAX_LENGTH)
        {
            throw new ServiceException("驳回原因不能超过 " + REASON_MAX_LENGTH + " 个字符");
        }

        SmtWorkRecord record = recordMapper.selectRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("记录不存在");
        }
        if (STATUS_APPROVED.equals(record.getStatus()))
        {
            throw new ServiceException("已确认的记录不能驳回");
        }
        if (STATUS_REJECTED.equals(record.getStatus()))
        {
            throw new ServiceException("该记录已驳回");
        }

        // 乐观锁：只有状态仍为 PENDING 才写入驳回原因
        int affected = recordMapper.rejectIfPending(recordId, trimmed, new Date());
        if (affected == 0)
        {
            throw new ServiceException("记录状态已变更，请刷新后重试");
        }

        log.info("驳回服务记录 id={}，原因={}，操作人={}",
                recordId, trimmed, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtWorkRecord reviseRecord(Long recordId, RevisionDTO dto)
    {
        if (recordId == null)
        {
            throw new ServiceException("记录ID不能为空");
        }
        if (dto == null)
        {
            throw new ServiceException("修正内容不能为空");
        }

        SmtWorkRecord record = recordMapper.selectRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("记录不存在");
        }
        if (!STATUS_APPROVED.equals(record.getStatus()))
        {
            throw new ServiceException("仅已确认的记录需要走修正流程");
        }

        String note = dto.getNote() == null ? null : dto.getNote().trim();
        if (note == null || note.isEmpty())
        {
            throw new ServiceException("请填写修正说明");
        }
        if (note.length() > NOTE_MAX_LENGTH)
        {
            throw new ServiceException("修正说明不能超过 " + NOTE_MAX_LENGTH + " 个字符");
        }

        // 保存修正前快照
        String beforeJson = snapshotJson(record);

        // 应用修改：字段为 null 表示不修改
        if (dto.getServiceDate() != null)
        {
            record.setServiceDate(dto.getServiceDate());
        }
        if (dto.getStartTime() != null)
        {
            String startTime = dto.getStartTime().trim();
            if (startTime.isEmpty())
            {
                throw new ServiceException("开始时间不能为空");
            }
            record.setStartTime(startTime);
        }
        if (dto.getEndTime() != null)
        {
            // 空白归一为 null，避免把空串写入 end_time
            String endTime = dto.getEndTime().trim();
            record.setEndTime(endTime.isEmpty() ? null : endTime);
        }
        if (dto.getLocation() != null)
        {
            String location = dto.getLocation().trim();
            if (location.isEmpty())
            {
                throw new ServiceException("服务地点不能为空");
            }
            if (location.length() > LOCATION_MAX_LENGTH)
            {
                throw new ServiceException("地点不能超过 " + LOCATION_MAX_LENGTH + " 个字符");
            }
            record.setLocation(location);
        }
        if (dto.getContent() != null)
        {
            String content = dto.getContent().trim();
            if (content.isEmpty())
            {
                throw new ServiceException("工作内容不能为空");
            }
            if (content.length() > CONTENT_MAX_LENGTH)
            {
                throw new ServiceException("工作内容不能超过 " + CONTENT_MAX_LENGTH + " 个字符");
            }
            record.setContent(content);
        }

        // 校验修正后的时间合法性
        if (record.getEndTime() != null && record.getEndTime().equals(record.getStartTime()))
        {
            throw new ServiceException("结束时间不能与开始时间相同");
        }

        String afterJson = snapshotJson(record);

        // 未检测到任何修改时不留痕
        if (beforeJson.equals(afterJson))
        {
            throw new ServiceException("未检测到任何修改");
        }

        // 写修正痕迹
        SmtRecordRevision revision = new SmtRecordRevision();
        revision.setRecordId(recordId);
        revision.setOperatorName(SecurityUtils.getUsername());
        revision.setRevisedAt(new Date());
        revision.setBeforeJson(beforeJson);
        revision.setAfterJson(afterJson);
        revision.setNote(note);
        revisionMapper.insertRevision(revision);

        record.setUpdateBy(SecurityUtils.getUsername());
        // 使用固定 SET 列表的语句，保证能把 end_time 显式置空
        recordMapper.updateRecordForRevision(record);

        log.info("修正已确认记录 id={}，说明={}，操作人={}",
                recordId, note, SecurityUtils.getUsername());
        return recordMapper.selectRecordById(recordId);
    }

    @Override
    public List<SmtRecordRevision> selectRevisions(Long recordId)
    {
        if (recordId == null)
        {
            throw new ServiceException("记录ID不能为空");
        }
        return revisionMapper.selectRevisionByRecordId(recordId);
    }

    /**
     * 生成修正快照 JSON。
     *
     * <p>只包含 5 个可编辑字段，与前端 {@code reviseRecord} 的 before/after 结构一致；
     * 日期统一转字符串，避免依赖 Jackson 的 JavaTimeModule。</p>
     *
     * @param record 服务记录
     * @return JSON 字符串
     */
    private String snapshotJson(SmtWorkRecord record)
    {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("serviceDate",
                record.getServiceDate() == null ? null : record.getServiceDate().toString());
        snapshot.put("startTime", record.getStartTime());
        snapshot.put("endTime", record.getEndTime());
        snapshot.put("location", record.getLocation());
        snapshot.put("content", record.getContent());

        try
        {
            return JSON.toJSONString(snapshot);
        }
        catch (RuntimeException e)
        {
            // 仅序列化字符串映射，正常不会失败
            throw new ServiceException("修正快照序列化失败：" + e.getMessage());
        }
    }
}
