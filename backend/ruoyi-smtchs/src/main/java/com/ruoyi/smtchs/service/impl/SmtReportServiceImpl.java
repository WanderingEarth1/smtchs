package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.smtchs.billing.BillingEngine;
import com.ruoyi.smtchs.billing.BillingInput;
import com.ruoyi.smtchs.billing.BillingMode;
import com.ruoyi.smtchs.billing.BillingResult;
import com.ruoyi.smtchs.billing.MonthlySummary;
import com.ruoyi.smtchs.billing.RoundingRule;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.EmployeeSummaryVO;
import com.ruoyi.smtchs.dto.MonthlyReportVO;
import com.ruoyi.smtchs.dto.WorkTypeSummaryVO;
import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import com.ruoyi.smtchs.service.ISmtReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 报表核算服务实现（STA-01 ~ STA-03）。
 *
 * <p>仅「已确认」记录计入核算；待确认与已驳回只做计数提示。
 * 单条金额一律由 {@link BillingEngine} 依据记录内的计费快照复算。</p>
 *
 * @author ruoyi
 */
@Service
public class SmtReportServiceImpl implements ISmtReportService
{
    private static final Logger log = LoggerFactory.getLogger(SmtReportServiceImpl.class);

    /** 月份格式 yyyy-MM */
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    /** 状态：待确认 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认 */
    private static final String STATUS_APPROVED = "APPROVED";

    /** 状态：已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 未知员工名称兜底 */
    private static final String UNKNOWN_EMPLOYEE = "未知员工";

    /** 未知类型名称兜底 */
    private static final String UNKNOWN_WORK_TYPE = "未知类型";

    /** 按金额降序排序 */
    private static final Comparator<MonthlySummary> AMOUNT_DESC =
            (a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount());

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    @Override
    public MonthlyReportVO buildMonthlyReport(String month, Long employeeId, Long workTypeId)
    {
        String monthKey = normalizeMonth(month);

        // 已确认记录：参与核算
        List<SmtWorkRecord> records =
                recordMapper.selectApprovedByMonth(monthKey, employeeId, workTypeId);
        if (records == null)
        {
            records = new ArrayList<>();
        }

        MonthlySummary summary = MonthlySummary.empty();
        Map<Long, MonthlySummary> byEmp = new LinkedHashMap<>();
        Map<Long, MonthlySummary> byType = new LinkedHashMap<>();
        Map<Long, String> empNames = new LinkedHashMap<>();
        Map<Long, String> typeNames = new LinkedHashMap<>();
        Map<Long, String> typeModes = new LinkedHashMap<>();

        for (SmtWorkRecord record : records)
        {
            BillingResult result = calculateSafe(record);
            BillingEngine.accumulate(summary, result);

            Long empId = record.getEmployeeId();
            byEmp.computeIfAbsent(empId, k -> MonthlySummary.empty());
            BillingEngine.accumulate(byEmp.get(empId), result);
            if (record.getEmployeeName() != null)
            {
                empNames.put(empId, record.getEmployeeName());
            }

            Long typeId = record.getWorkTypeId();
            byType.computeIfAbsent(typeId, k -> MonthlySummary.empty());
            BillingEngine.accumulate(byType.get(typeId), result);
            if (record.getWorkTypeName() != null)
            {
                typeNames.put(typeId, record.getWorkTypeName());
            }
            if (record.getSnapBillingMode() != null)
            {
                typeModes.put(typeId, record.getSnapBillingMode());
            }
        }

        // 按员工汇总，金额降序
        List<EmployeeSummaryVO> byEmployee = new ArrayList<>();
        for (Map.Entry<Long, MonthlySummary> entry : byEmp.entrySet())
        {
            String name = empNames.get(entry.getKey());
            byEmployee.add(new EmployeeSummaryVO(
                    entry.getKey(),
                    name == null ? UNKNOWN_EMPLOYEE : name,
                    entry.getValue()));
        }
        byEmployee.sort((a, b) -> AMOUNT_DESC.compare(a.getSummary(), b.getSummary()));

        // 按工作类型汇总，金额降序
        List<WorkTypeSummaryVO> byWorkType = new ArrayList<>();
        for (Map.Entry<Long, MonthlySummary> entry : byType.entrySet())
        {
            String name = typeNames.get(entry.getKey());
            String mode = typeModes.get(entry.getKey());
            byWorkType.add(new WorkTypeSummaryVO(
                    entry.getKey(),
                    name == null ? UNKNOWN_WORK_TYPE : name,
                    mode == null ? BillingMode.BY_COUNT.name() : mode,
                    entry.getValue()));
        }
        byWorkType.sort((a, b) -> AMOUNT_DESC.compare(a.getSummary(), b.getSummary()));

        // 统计未计入核算的条数（STA-03）
        int pendingCount = 0;
        int rejectedCount = 0;
        for (SmtWorkRecord record : selectMonthRecords(monthKey, employeeId, workTypeId))
        {
            if (STATUS_PENDING.equals(record.getStatus()))
            {
                pendingCount++;
            }
            else if (STATUS_REJECTED.equals(record.getStatus()))
            {
                rejectedCount++;
            }
        }

        MonthlyReportVO vo = new MonthlyReportVO();
        vo.setMonthKey(monthKey);
        vo.setSummary(summary);
        vo.setByEmployee(byEmployee);
        vo.setByWorkType(byWorkType);
        vo.setApprovedRecords(records);
        vo.setPendingCount(pendingCount);
        vo.setRejectedCount(rejectedCount);

        log.info("生成月度报表 {}：已确认 {} 条，待确认 {} 条，已驳回 {} 条，合计 {} 元",
                monthKey, records.size(), pendingCount, rejectedCount, summary.getTotalAmount());
        return vo;
    }

    @Override
    public MonthlyReportVO buildMyMonthlySummary(Long employeeId, String month)
    {
        if (employeeId == null)
        {
            throw new ServiceException("员工ID不能为空");
        }

        String monthKey = normalizeMonth(month);
        List<SmtWorkRecord> records = recordMapper.selectMyRecords(employeeId, monthKey, null);
        if (records == null)
        {
            records = new ArrayList<>();
        }

        MonthlySummary summary = MonthlySummary.empty();
        List<SmtWorkRecord> approved = new ArrayList<>();
        int pendingCount = 0;
        int rejectedCount = 0;

        for (SmtWorkRecord record : records)
        {
            if (STATUS_APPROVED.equals(record.getStatus()))
            {
                BillingEngine.accumulate(summary, calculateSafe(record));
                approved.add(record);
            }
            else if (STATUS_PENDING.equals(record.getStatus()))
            {
                pendingCount++;
            }
            else if (STATUS_REJECTED.equals(record.getStatus()))
            {
                rejectedCount++;
            }
        }

        MonthlyReportVO vo = new MonthlyReportVO();
        vo.setMonthKey(monthKey);
        vo.setSummary(summary);
        vo.setApprovedRecords(approved);
        vo.setPendingCount(pendingCount);
        vo.setRejectedCount(rejectedCount);
        return vo;
    }

    /**
     * 查询指定月份的全部记录（含各状态），用于统计待确认与已驳回条数。
     *
     * <p>借助 {@code BaseEntity.params} 的 beginServiceDate / endServiceDate
     * 限定服务日期区间，避免额外新增 Mapper 方法。</p>
     */
    private List<SmtWorkRecord> selectMonthRecords(String monthKey, Long employeeId, Long workTypeId)
    {
        YearMonth ym = YearMonth.parse(monthKey);
        LocalDate begin = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        SmtWorkRecord query = new SmtWorkRecord();
        query.setEmployeeId(employeeId);
        query.setWorkTypeId(workTypeId);
        query.getParams().put("beginServiceDate", begin.toString());
        query.getParams().put("endServiceDate", end.toString());

        List<SmtWorkRecord> list = recordMapper.selectRecordList(query);
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 安全核算：单条记录数据异常时返回 0 金额结果，避免整张报表失败。
     *
     * @param record 服务记录
     * @return 计费结果
     */
    private BillingResult calculateSafe(SmtWorkRecord record)
    {
        try
        {
            return BillingEngine.calculate(toBillingInput(record));
        }
        catch (RuntimeException e)
        {
            log.warn("记录 id={} 核算失败，按 0 元计入：{}", record.getId(), e.getMessage());
            return BillingResult.builder()
                    .billingMode(parseBillingModeQuietly(record.getSnapBillingMode()))
                    .count(0)
                    .normalHours(BigDecimal.ZERO)
                    .overtimeHours(BigDecimal.ZERO)
                    .normalAmount(BigDecimal.ZERO)
                    .overtimeAmount(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    .rawNormalHours(BigDecimal.ZERO)
                    .rawOvertimeHours(BigDecimal.ZERO)
                    .crossMidnight(false)
                    .explanation("数据异常，无法核算")
                    .build();
        }
    }

    /**
     * 把记录内的计费快照转换为计费引擎入参。
     */
    private BillingInput toBillingInput(SmtWorkRecord record)
    {
        return BillingInput.builder()
                .billingMode(parseBillingMode(record.getSnapBillingMode()))
                .unitPrice(record.getSnapUnitPrice())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .overtimeStart(record.getSnapOvertimeStart())
                .overtimeMultiplier(record.getSnapOvertimeMultiplier())
                .roundingRule(parseRoundingRule(record.getSnapRoundingRule()))
                .build();
    }

    /**
     * 解析计费模式快照，非法值抛出异常（由 calculateSafe 兜底）。
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
        throw new IllegalArgumentException("计费模式快照非法：" + value);
    }

    /**
     * 解析计费模式快照，非法值降级为按次（仅用于异常兜底结果）。
     */
    private BillingMode parseBillingModeQuietly(String value)
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
        return BillingMode.BY_COUNT;
    }

    /**
     * 解析取整规则快照，非法值抛出异常（由 calculateSafe 兜底）。
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
        throw new IllegalArgumentException("取整规则快照非法：" + value);
    }

    /**
     * 归一化月份：为空取当前月，并校验 yyyy-MM 格式。
     *
     * @param month 月份
     * @return 规范化后的月份
     */
    private String normalizeMonth(String month)
    {
        if (month == null || month.trim().isEmpty())
        {
            return YearMonth.now().toString();
        }
        String trimmed = month.trim();
        if (!MONTH_PATTERN.matcher(trimmed).matches())
        {
            throw new ServiceException("月份格式应为 yyyy-MM");
        }
        try
        {
            YearMonth.parse(trimmed);
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("月份格式应为 yyyy-MM");
        }
        return trimmed;
    }
}
