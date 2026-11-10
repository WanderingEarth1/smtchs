package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.billing.BillingEngine;
import com.ruoyi.smtchs.billing.BillingInput;
import com.ruoyi.smtchs.billing.BillingMode;
import com.ruoyi.smtchs.billing.BillingResult;
import com.ruoyi.smtchs.billing.RoundingRule;
import com.ruoyi.smtchs.domain.SmtRecordPhoto;
import com.ruoyi.smtchs.domain.SmtSysConfig;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.domain.SmtWorkType;
import com.ruoyi.smtchs.dto.RecordDraftDTO;
import com.ruoyi.smtchs.mapper.SmtPriceRuleMapper;
import com.ruoyi.smtchs.mapper.SmtSysConfigMapper;
import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import com.ruoyi.smtchs.mapper.SmtWorkTypeMapper;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import com.ruoyi.smtchs.service.ISmtRecordService;
import com.ruoyi.smtchs.util.SerialNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 服务记录服务实现（REC-01 ~ REC-05）。
 *
 * <p>业务规则严格对齐前端 {@code src/data/service.ts} 的
 * {@code validateDraft} / {@code createRecord} / {@code updateRecord} / {@code deleteRecord}。</p>
 *
 * @author ruoyi
 */
@Service
public class SmtRecordServiceImpl implements ISmtRecordService
{
    private static final Logger log = LoggerFactory.getLogger(SmtRecordServiceImpl.class);

    /** 照片数量下限 */
    private static final int MIN_PHOTOS = 1;

    /** 照片数量上限 */
    private static final int MAX_PHOTOS = 3;

    /** 地点最大长度 */
    private static final int LOCATION_MAX_LENGTH = 60;

    /** 工作内容最大长度 */
    private static final int CONTENT_MAX_LENGTH = 200;

    /** 备注最大长度 */
    private static final int REMARK_MAX_LENGTH = 200;

    /** 状态：待确认 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认 */
    private static final String STATUS_APPROVED = "APPROVED";

    /** 是否标记：是 */
    private static final Integer FLAG_YES = 1;

    /** 是否标记：否 */
    private static final Integer FLAG_NO = 0;

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    @Autowired
    private SmtWorkTypeMapper workTypeMapper;

    @Autowired
    private SmtPriceRuleMapper priceMapper;

    @Autowired
    private SmtSysConfigMapper configMapper;

    @Autowired
    private SerialNoGenerator serialNoGenerator;

    @Autowired
    private ISmtEmployeeService employeeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtWorkRecord createRecord(RecordDraftDTO draft)
    {
        Long employeeId = employeeService.getCurrentEmployeeId();

        // 1. 校验草稿（基本字段）
        validateDraft(draft);

        // 2. 取工作类型与单价
        SmtWorkType workType = workTypeMapper.selectWorkTypeById(draft.getWorkTypeId());
        if (workType == null)
        {
            throw new ServiceException("工作类型不存在");
        }
        if (workType.getEnabled() == null || workType.getEnabled() != 1)
        {
            throw new ServiceException("该工作类型已停用");
        }
        // 按小时类型的结束时间规则依赖工作类型，故在此补校验
        validateHourlyDraft(draft, workType);

        BigDecimal unitPrice = priceMapper.selectPrice(employeeId, draft.getWorkTypeId());
        if (unitPrice == null || unitPrice.signum() <= 0)
        {
            throw new ServiceException("该工作类型尚未配置单价，请联系管理员");
        }

        // 3. 取当前计费配置
        SmtSysConfig config = configMapper.selectConfig();
        if (config == null)
        {
            throw new ServiceException("计费配置尚未初始化，请联系管理员");
        }

        // 4. 组装记录（含计费快照）
        SmtWorkRecord record = new SmtWorkRecord();
        record.setSerialNo(serialNoGenerator.next());
        record.setEmployeeId(employeeId);
        record.setWorkTypeId(draft.getWorkTypeId());
        record.setServiceDate(draft.getServiceDate());
        record.setStartTime(trim(draft.getStartTime()));
        record.setEndTime(isByHour(workType) ? trim(draft.getEndTime()) : null);
        record.setLocation(trim(draft.getLocation()));
        record.setLatitude(draft.getLatitude());
        record.setLongitude(draft.getLongitude());
        record.setGeoCaptured(Boolean.TRUE.equals(draft.getGeoCaptured()) ? FLAG_YES : FLAG_NO);
        record.setContent(trim(draft.getContent()));
        record.setRemark(trim(draft.getRemark()));
        record.setStatus(STATUS_PENDING);
        // REC-02：非当日提交自动标记补录
        record.setIsBackfill(draft.getServiceDate().equals(LocalDate.now()) ? FLAG_NO : FLAG_YES);
        record.setSubmittedAt(new Date());
        record.setCreateBy(SecurityUtils.getUsername());
        // 计费快照
        record.setSnapBillingMode(workType.getBillingMode());
        record.setSnapUnitPrice(unitPrice);
        record.setSnapOvertimeStart(config.getOvertimeStart());
        record.setSnapOvertimeMultiplier(config.getOvertimeMultiplier());
        record.setSnapRoundingRule(config.getRoundingRule());

        recordMapper.insertRecord(record);

        // 5. 保存照片
        savePhotos(record.getId(), draft.getPhotoUrls());

        log.info("新增服务记录 id={}，流水号={}，员工={}，类型={}",
                record.getId(), record.getSerialNo(), employeeId, draft.getWorkTypeId());

        return recordMapper.selectRecordById(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtWorkRecord updateRecord(Long recordId, RecordDraftDTO draft)
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

        Long employeeId = employeeService.getCurrentEmployeeId();
        if (!employeeId.equals(record.getEmployeeId()))
        {
            throw new ServiceException("只能修改自己的记录");
        }
        if (STATUS_APPROVED.equals(record.getStatus()))
        {
            throw new ServiceException("已确认的记录已锁定，无法修改");
        }

        validateDraft(draft);

        SmtWorkType workType = workTypeMapper.selectWorkTypeById(draft.getWorkTypeId());
        if (workType == null)
        {
            throw new ServiceException("工作类型不存在");
        }
        if (workType.getEnabled() == null || workType.getEnabled() != 1)
        {
            throw new ServiceException("该工作类型已停用");
        }
        validateHourlyDraft(draft, workType);

        BigDecimal unitPrice = priceMapper.selectPrice(employeeId, draft.getWorkTypeId());
        if (unitPrice == null || unitPrice.signum() <= 0)
        {
            throw new ServiceException("该工作类型尚未配置单价，请联系管理员");
        }

        SmtSysConfig config = configMapper.selectConfig();
        if (config == null)
        {
            throw new ServiceException("计费配置尚未初始化，请联系管理员");
        }

        // 修改后重新提交，重走审核（AUD-05）
        record.setWorkTypeId(draft.getWorkTypeId());
        record.setServiceDate(draft.getServiceDate());
        record.setStartTime(trim(draft.getStartTime()));
        record.setEndTime(isByHour(workType) ? trim(draft.getEndTime()) : null);
        record.setLocation(trim(draft.getLocation()));
        record.setLatitude(draft.getLatitude());
        record.setLongitude(draft.getLongitude());
        record.setGeoCaptured(Boolean.TRUE.equals(draft.getGeoCaptured()) ? FLAG_YES : FLAG_NO);
        record.setContent(trim(draft.getContent()));
        record.setRemark(trim(draft.getRemark()));
        record.setStatus(STATUS_PENDING);
        record.setIsBackfill(draft.getServiceDate().equals(LocalDate.now()) ? FLAG_NO : FLAG_YES);
        record.setSubmittedAt(new Date());
        record.setUpdateBy(SecurityUtils.getUsername());
        // 重新固化计费快照
        record.setSnapBillingMode(workType.getBillingMode());
        record.setSnapUnitPrice(unitPrice);
        record.setSnapOvertimeStart(config.getOvertimeStart());
        record.setSnapOvertimeMultiplier(config.getOvertimeMultiplier());
        record.setSnapRoundingRule(config.getRoundingRule());

        // 使用固定 SET 列表的语句，保证「按小时 → 按次」时能把 end_time 置空
        int affected = recordMapper.updateRecordForResubmit(record);
        if (affected == 0)
        {
            throw new ServiceException("记录状态已变更，请刷新后重试");
        }

        // 照片整体替换
        recordMapper.deletePhotosByRecordId(recordId);
        savePhotos(recordId, draft.getPhotoUrls());

        log.info("修改服务记录 id={}，员工={}，重新提交审核", recordId, employeeId);
        return recordMapper.selectRecordById(recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long recordId)
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

        Long employeeId = employeeService.getCurrentEmployeeId();
        if (!employeeId.equals(record.getEmployeeId()))
        {
            throw new ServiceException("只能删除自己的记录");
        }
        if (STATUS_APPROVED.equals(record.getStatus()))
        {
            throw new ServiceException("已确认的记录已锁定，无法删除");
        }

        recordMapper.deleteRecordById(recordId);
        log.info("删除服务记录 id={}，员工={}", recordId, employeeId);
    }

    @Override
    public SmtWorkRecord selectRecordById(Long recordId)
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
        return record;
    }

    @Override
    public List<SmtWorkRecord> selectRecordList(SmtWorkRecord query)
    {
        return recordMapper.selectRecordList(query == null ? new SmtWorkRecord() : query);
    }

    @Override
    public List<SmtWorkRecord> selectMyRecords(String month, String status)
    {
        return selectMyRecords(employeeService.getCurrentEmployeeId(), month, status);
    }

    @Override
    public List<SmtWorkRecord> selectMyRecords(Long employeeId, String month, String status)
    {
        if (employeeId == null)
        {
            throw new ServiceException("员工ID不能为空");
        }
        return recordMapper.selectMyRecords(employeeId, trimToNull(month), trimToNull(status));
    }

    @Override
    public BillingResult calculateRecord(SmtWorkRecord record)
    {
        if (record == null)
        {
            throw new ServiceException("记录不能为空");
        }
        return BillingEngine.calculate(toBillingInput(record));
    }

    @Override
    public int countPending()
    {
        return recordMapper.countByStatus(STATUS_PENDING);
    }

    /**
     * 把记录内的计费快照转换为计费引擎入参。
     *
     * <p>刻意只读 {@code snap_*} 字段，保证历史记录不受当前配置变更影响。</p>
     *
     * @param record 服务记录
     * @return 计费入参
     */
    private BillingInput toBillingInput(SmtWorkRecord record)
    {
        BillingMode billingMode = parseBillingMode(record.getSnapBillingMode());
        RoundingRule roundingRule = parseRoundingRule(record.getSnapRoundingRule());

        return BillingInput.builder()
                .billingMode(billingMode)
                .unitPrice(record.getSnapUnitPrice())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .overtimeStart(record.getSnapOvertimeStart())
                .overtimeMultiplier(record.getSnapOvertimeMultiplier())
                .roundingRule(roundingRule)
                .build();
    }

    /**
     * 校验记录草稿的「与工作类型无关」的字段规则，对齐前端 {@code validateDraft}。
     *
     * <p>按小时类型的结束时间规则依赖工作类型，由 {@link #validateHourlyDraft} 单独校验，
     * 避免重复查询工作类型。</p>
     *
     * @param draft 记录草稿
     */
    private void validateDraft(RecordDraftDTO draft)
    {
        if (draft == null)
        {
            throw new ServiceException("记录内容不能为空");
        }

        // 工作类型必填
        if (draft.getWorkTypeId() == null)
        {
            throw new ServiceException("请选择工作类型");
        }

        // 日期必填，不能是未来
        if (draft.getServiceDate() == null)
        {
            throw new ServiceException("请选择服务日期");
        }
        if (draft.getServiceDate().isAfter(LocalDate.now()))
        {
            throw new ServiceException("不能填写未来日期");
        }

        // 开始时间必填
        String startTime = trimToNull(draft.getStartTime());
        if (startTime == null)
        {
            throw new ServiceException("请填写开始时间");
        }
        if (!isValidTime(startTime))
        {
            throw new ServiceException("开始时间格式应为 HH:mm");
        }

        // 地点必填，≤60 字符
        String location = trimToNull(draft.getLocation());
        if (location == null)
        {
            throw new ServiceException("请填写服务地点");
        }
        if (location.length() > LOCATION_MAX_LENGTH)
        {
            throw new ServiceException("地点不能超过 " + LOCATION_MAX_LENGTH + " 个字符");
        }

        // 工作内容必填，≤200 字符
        String content = trimToNull(draft.getContent());
        if (content == null)
        {
            throw new ServiceException("请填写工作内容");
        }
        if (content.length() > CONTENT_MAX_LENGTH)
        {
            throw new ServiceException("工作内容不能超过 " + CONTENT_MAX_LENGTH + " 个字符");
        }

        // 照片 1~3 张
        List<String> photoUrls = draft.getPhotoUrls();
        int photoCount = 0;
        if (photoUrls != null)
        {
            for (String url : photoUrls)
            {
                if (trimToNull(url) != null)
                {
                    photoCount++;
                }
            }
        }
        if (photoCount < MIN_PHOTOS)
        {
            throw new ServiceException("请至少拍摄 1 张现场凭证");
        }
        if (photoCount > MAX_PHOTOS)
        {
            throw new ServiceException("最多上传 " + MAX_PHOTOS + " 张照片");
        }

        // 备注 ≤200 字符
        String remark = trimToNull(draft.getRemark());
        if (remark != null && remark.length() > REMARK_MAX_LENGTH)
        {
            throw new ServiceException("备注不能超过 " + REMARK_MAX_LENGTH + " 个字符");
        }
    }

    /**
     * 校验按小时计费类型特有的结束时间规则。
     *
     * @param draft    记录草稿
     * @param workType 已查出的工作类型
     */
    private void validateHourlyDraft(RecordDraftDTO draft, SmtWorkType workType)
    {
        if (!isByHour(workType))
        {
            return;
        }

        String startTime = trimToNull(draft.getStartTime());
        String endTime = trimToNull(draft.getEndTime());
        if (endTime == null)
        {
            throw new ServiceException("按小时计费需填写结束时间");
        }
        if (!isValidTime(endTime))
        {
            throw new ServiceException("结束时间格式应为 HH:mm");
        }
        if (endTime.equals(startTime))
        {
            throw new ServiceException("结束时间不能与开始时间相同");
        }
    }

    /**
     * 保存记录照片，按传入顺序写入 sort。
     *
     * @param recordId  记录ID
     * @param photoUrls 照片URL列表
     */
    private void savePhotos(Long recordId, List<String> photoUrls)
    {
        if (photoUrls == null || photoUrls.isEmpty())
        {
            return;
        }
        int sort = 0;
        for (String url : photoUrls)
        {
            String trimmed = trimToNull(url);
            if (trimmed == null)
            {
                continue;
            }
            SmtRecordPhoto photo = new SmtRecordPhoto();
            photo.setRecordId(recordId);
            photo.setUrl(trimmed);
            photo.setSort(sort++);
            recordMapper.insertPhoto(photo);
        }
    }

    /**
     * 判断工作类型是否为按小时计费。
     */
    private boolean isByHour(SmtWorkType workType)
    {
        return BillingMode.BY_HOUR.name().equals(workType.getBillingMode());
    }

    /**
     * 解析计费模式快照，非法值抛出业务异常。
     */
    private BillingMode parseBillingMode(String value)
    {
        if (value != null)
        {
            for (BillingMode item : BillingMode.values())
            {
                if (item.name().equals(value))
                {
                    return item;
                }
            }
        }
        throw new ServiceException("记录的计费模式快照非法：" + value);
    }

    /**
     * 解析取整规则快照，非法值抛出业务异常。
     */
    private RoundingRule parseRoundingRule(String value)
    {
        if (value != null)
        {
            for (RoundingRule item : RoundingRule.values())
            {
                if (item.name().equals(value))
                {
                    return item;
                }
            }
        }
        throw new ServiceException("记录的取整规则快照非法：" + value);
    }

    /**
     * 校验 HH:mm 格式时间。
     */
    private boolean isValidTime(String time)
    {
        try
        {
            BillingEngine.parseTimeToMinutes(time);
            return true;
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    /**
     * 去除首尾空白，null 原样返回。
     */
    private String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    /**
     * 去除首尾空白，空串归一为 null。
     */
    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
