package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.billing.BillingEngine;
import com.ruoyi.smtchs.billing.BillingInput;
import com.ruoyi.smtchs.billing.BillingMode;
import com.ruoyi.smtchs.billing.BillingResult;
import com.ruoyi.smtchs.billing.RoundingRule;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.EmployeeSummaryVO;
import com.ruoyi.smtchs.dto.MonthlyReportVO;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import com.ruoyi.smtchs.service.ISmtReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表核算控制器（STA-01 ~ STA-03 / REC-05）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/report")
public class SmtReportController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SmtReportController.class);

    /** 状态：待确认 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认 */
    private static final String STATUS_APPROVED = "APPROVED";

    /** 状态：已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 计费模式：按小时 */
    private static final String MODE_BY_HOUR = "BY_HOUR";

    /** 工作内容摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 60;

    /** 未获取定位的展示文案 */
    private static final String NO_GEO_TEXT = "未获取定位";

    /** 无照片的展示文案 */
    private static final String NO_PHOTO_TEXT = "无";

    /** 按次记录无结束时间的展示文案 */
    private static final String NO_END_TIME_TEXT = "—";

    /** 导出文件名前缀 */
    private static final String EXPORT_FILE_PREFIX = "月度报表_";

    @Autowired
    private ISmtReportService reportService;

    @Autowired
    private ISmtEmployeeService employeeService;

    /**
     * 生成月度报表（STA-01 / STA-02 / STA-03）。
     *
     * @param month      月份 yyyy-MM
     * @param employeeId 员工ID，可空
     * @param workTypeId 工作类型ID，可空
     * @return 月度报表
     */
    @PreAuthorize("@ss.hasPermi('smtchs:report:list')")
    @GetMapping("/monthly")
    public AjaxResult monthly(@RequestParam String month,
                              @RequestParam(required = false) Long employeeId,
                              @RequestParam(required = false) Long workTypeId)
    {
        return AjaxResult.success("操作成功",
                reportService.buildMonthlyReport(month, employeeId, workTypeId));
    }

    /**
     * 员工本人当月汇总（REC-05），无需管理权限。
     *
     * @param month 月份 yyyy-MM
     * @return 月度报表（不含按员工 / 按类型汇总）
     */
    @GetMapping("/my-summary")
    public AjaxResult mySummary(@RequestParam String month)
    {
        Long employeeId = employeeService.getCurrentEmployeeId();
        return AjaxResult.success("操作成功",
                reportService.buildMyMonthlySummary(employeeId, month));
    }

    /**
     * 导出月度报表（双 Sheet：明细表 + 按人汇总表）。
     *
     * @param month    月份 yyyy-MM
     * @param response 响应对象
     * @throws IOException 写出失败
     */
    @PreAuthorize("@ss.hasPermi('smtchs:report:export')")
    @Log(title = "月度报表", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@RequestParam String month, HttpServletResponse response) throws IOException
    {
        MonthlyReportVO report = reportService.buildMonthlyReport(month, null, null);

        List<RecordDetailExcelVO> detailList = new ArrayList<>();
        List<SmtWorkRecord> records = report.getApprovedRecords();
        if (records != null)
        {
            for (SmtWorkRecord record : records)
            {
                detailList.add(toDetailVO(record));
            }
        }

        List<EmployeeSummaryExcelVO> summaryList = new ArrayList<>();
        List<EmployeeSummaryVO> byEmployee = report.getByEmployee();
        if (byEmployee != null)
        {
            for (EmployeeSummaryVO item : byEmployee)
            {
                summaryList.add(toSummaryVO(item));
            }
        }

        SmtExcelExporter.export(response, EXPORT_FILE_PREFIX + report.getMonthKey(),
                detailList, summaryList);
    }

    /**
     * 服务记录 → 明细表行。
     *
     * @param record 服务记录
     * @return 明细行
     */
    private RecordDetailExcelVO toDetailVO(SmtWorkRecord record)
    {
        RecordDetailExcelVO vo = new RecordDetailExcelVO();
        vo.setSerialNo(record.getSerialNo());
        vo.setEmployeeName(record.getEmployeeName());
        vo.setServiceDate(record.getServiceDate() == null ? "" : record.getServiceDate().toString());
        vo.setWorkTypeName(record.getWorkTypeName());
        vo.setBillingMode(MODE_BY_HOUR.equals(record.getSnapBillingMode()) ? "按小时" : "按次");
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime() == null ? NO_END_TIME_TEXT : record.getEndTime());
        vo.setLocation(record.getLocation());
        vo.setGeoText(buildGeoText(record));
        vo.setContentSummary(summarize(record.getContent(), SUMMARY_MAX_LENGTH));
        vo.setPhotoLinks(buildPhotoLinks(record));
        vo.setStatusText(statusText(record.getStatus()));
        vo.setBackfillText(isFlagOn(record.getIsBackfill()) ? "是" : "否");
        vo.setUnitPrice(record.getSnapUnitPrice());

        // 金额与时长用计费引擎依据记录内的快照复算
        BillingResult billing = calculateSafe(record);
        vo.setNormalHours(billing.getNormalHours());
        vo.setOvertimeHours(billing.getOvertimeHours());
        vo.setAmount(billing.getTotal());
        return vo;
    }

    /**
     * 按员工汇总 → 汇总表行。
     *
     * @param item 按员工汇总
     * @return 汇总行
     */
    private EmployeeSummaryExcelVO toSummaryVO(EmployeeSummaryVO item)
    {
        EmployeeSummaryExcelVO vo = new EmployeeSummaryExcelVO();
        vo.setEmployeeName(item.getEmployeeName());
        if (item.getSummary() != null)
        {
            vo.setCountTotal(item.getSummary().getCountTotal());
            vo.setNormalHoursTotal(item.getSummary().getNormalHoursTotal());
            vo.setOvertimeHoursTotal(item.getSummary().getOvertimeHoursTotal());
            vo.setByCountAmount(item.getSummary().getByCountAmount());
            vo.setByHourAmount(item.getSummary().getByHourAmount());
            vo.setTotalAmount(item.getSummary().getTotalAmount());
        }
        else
        {
            vo.setCountTotal(0);
            vo.setNormalHoursTotal(BigDecimal.ZERO);
            vo.setOvertimeHoursTotal(BigDecimal.ZERO);
            vo.setByCountAmount(BigDecimal.ZERO);
            vo.setByHourAmount(BigDecimal.ZERO);
            vo.setTotalAmount(BigDecimal.ZERO);
        }
        return vo;
    }

    /**
     * 安全核算：单条记录数据异常时返回 0 金额结果，避免整张报表导出失败。
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
            log.warn("导出时记录 id={} 核算失败，按 0 元计入：{}", record.getId(), e.getMessage());
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
     *
     * @param record 服务记录
     * @return 计费入参
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
     * 定位展示：已获取定位输出「纬度,经度」，否则输出「未获取定位」。
     *
     * @param record 服务记录
     * @return 定位文案
     */
    private String buildGeoText(SmtWorkRecord record)
    {
        if (!isFlagOn(record.getGeoCaptured()))
        {
            return NO_GEO_TEXT;
        }
        return record.getLatitude() + "," + record.getLongitude();
    }

    /**
     * 照片展示：输出凭证张数，无照片输出「无」。
     *
     * @param record 服务记录
     * @return 照片文案
     */
    private String buildPhotoLinks(SmtWorkRecord record)
    {
        if (record.getPhotos() == null || record.getPhotos().isEmpty())
        {
            return NO_PHOTO_TEXT;
        }
        return "凭证" + record.getPhotos().size() + "张";
    }

    /**
     * 截断文本并追加省略号。
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 摘要文本
     */
    private String summarize(String text, int maxLength)
    {
        if (text == null || text.trim().isEmpty())
        {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength)
        {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "…";
    }

    /**
     * 状态码转中文。
     *
     * @param status 状态码
     * @return 状态中文
     */
    private String statusText(String status)
    {
        if (STATUS_PENDING.equals(status))
        {
            return "待确认";
        }
        if (STATUS_APPROVED.equals(status))
        {
            return "已确认";
        }
        if (STATUS_REJECTED.equals(status))
        {
            return "已驳回";
        }
        return status == null ? "" : status;
    }

    /**
     * 判断 1/0 标志位是否为「是」。
     *
     * @param flag 标志位
     * @return true 表示是
     */
    private boolean isFlagOn(Integer flag)
    {
        return flag != null && flag == 1;
    }

    /**
     * 解析计费模式快照，非法值抛出异常（由 calculateSafe 兜底）。
     *
     * @param value 计费模式快照
     * @return 计费模式
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
     *
     * @param value 计费模式快照
     * @return 计费模式
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
     *
     * @param value 取整规则快照
     * @return 取整规则
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
}
