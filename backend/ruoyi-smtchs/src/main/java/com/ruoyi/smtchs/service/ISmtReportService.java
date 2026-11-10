package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.dto.MonthlyReportVO;

/**
 * 报表核算服务接口（STA-01 ~ STA-03）。
 *
 * <p>仅「已确认」记录计入核算，待确认与已驳回只做计数提示。</p>
 *
 * @author ruoyi
 */
public interface ISmtReportService
{
    /**
     * 生成月度报表（STA-01 / STA-02 / STA-03）。
     *
     * @param month      月份，格式 yyyy-MM，为空时取当前月
     * @param employeeId 员工ID，可为 null
     * @param workTypeId 工作类型ID，可为 null
     * @return 月度报表
     */
    MonthlyReportVO buildMonthlyReport(String month, Long employeeId, Long workTypeId);

    /**
     * 生成员工本人当月汇总（REC-05）。
     *
     * @param employeeId 员工ID
     * @param month      月份，格式 yyyy-MM，为空时取当前月
     * @return 月度报表（{@code byEmployee} / {@code byWorkType} 为空）
     */
    MonthlyReportVO buildMyMonthlySummary(Long employeeId, String month);
}
