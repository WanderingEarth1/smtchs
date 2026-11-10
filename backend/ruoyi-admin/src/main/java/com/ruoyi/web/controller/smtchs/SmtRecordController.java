package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.RecordDraftDTO;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import com.ruoyi.smtchs.service.ISmtRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务记录控制器（REC-01 ~ REC-05）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/record")
public class SmtRecordController extends BaseController
{
    @Autowired
    private ISmtRecordService recordService;

    @Autowired
    private ISmtEmployeeService employeeService;

    /**
     * 查询当前员工的服务记录（REC-04）。
     *
     * @param month  月份 yyyy-MM
     * @param status 状态，可空
     * @return 服务记录集合
     */
    @GetMapping("/my")
    public AjaxResult myRecords(@RequestParam String month,
                                @RequestParam(required = false) String status)
    {
        return AjaxResult.success("操作成功", recordService.selectMyRecords(month, status));
    }

    /**
     * 查询服务记录列表（管理端）。
     *
     * @param query 查询条件
     * @return 分页后的记录列表
     */
    @PreAuthorize("@ss.hasPermi('smtchs:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(SmtWorkRecord query)
    {
        startPage();
        return getDataTable(recordService.selectRecordList(query));
    }

    /**
     * 查询记录详情（含照片）。
     *
     * @param id 记录ID
     * @return 服务记录
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        SmtWorkRecord record = recordService.selectRecordById(id);
        if (record == null)
        {
            return AjaxResult.error("记录不存在");
        }
        // 归属校验：员工只能看自己的记录，管理员可看全部（防 IDOR）
        Long currentEmployeeId = employeeService.getCurrentEmployeeId();
        boolean isAdmin = SecurityUtils.isAdmin(SecurityUtils.getUserId());
        if (!isAdmin && !record.getEmployeeId().equals(currentEmployeeId))
        {
            return AjaxResult.error("无权查看该记录");
        }
        return AjaxResult.success("操作成功", record);
    }

    /**
     * 新增服务记录（REC-01 / REC-02）。
     *
     * @param draft 记录草稿
     * @return 新增后的服务记录
     */
    // 员工端 H5 是唯一调用方，权限由 Service 层「本人」校验保障，不挂管理端菜单权限
    @Log(title = "服务记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody RecordDraftDTO draft)
    {
        return AjaxResult.success("操作成功", recordService.createRecord(draft));
    }

    /**
     * 修改服务记录（REC-03）。
     *
     * <p>{@link RecordDraftDTO} 只承载可编辑字段，不含主键，
     * 故记录ID 由查询参数 {@code id} 传入。</p>
     *
     * @param id    记录ID
     * @param draft 记录草稿
     * @return 修改后的服务记录
     */
    @Log(title = "服务记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestParam Long id, @RequestBody RecordDraftDTO draft)
    {
        return AjaxResult.success("操作成功", recordService.updateRecord(id, draft));
    }

    /**
     * 删除服务记录（REC-03）。
     *
     * @param id 记录ID
     * @return 成功结果
     */
    @Log(title = "服务记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        recordService.deleteRecord(id);
        return success();
    }
}
