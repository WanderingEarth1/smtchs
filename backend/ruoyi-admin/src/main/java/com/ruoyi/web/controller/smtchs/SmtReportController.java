package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import com.ruoyi.smtchs.service.ISmtReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 报表核算控制器（STA-01 ~ STA-03 / REC-05）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/report")
public class SmtReportController extends BaseController
{
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
     * 导出月度报表。
     *
     * <p>TODO Task 5c：改为双 Sheet 导出（明细 Sheet + 员工汇总 Sheet），
     * 当前先输出占位提示，暂不引入 EasyExcel 依赖。</p>
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
        // TODO Task 5c：实现双 Sheet 导出（明细 + 员工汇总）
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("月度报表导出待实现（Task 5c），月份：" + month);
    }
}
