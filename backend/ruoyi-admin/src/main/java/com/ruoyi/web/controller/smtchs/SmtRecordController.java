package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.RecordDraftDTO;
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
        return AjaxResult.success("操作成功", recordService.selectRecordById(id));
    }

    /**
     * 新增服务记录（REC-01 / REC-02）。
     *
     * @param draft 记录草稿
     * @return 新增后的服务记录
     */
    @PreAuthorize("@ss.hasPermi('smtchs:record:add')")
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
    @PreAuthorize("@ss.hasPermi('smtchs:record:edit')")
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
    @PreAuthorize("@ss.hasPermi('smtchs:record:remove')")
    @Log(title = "服务记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        recordService.deleteRecord(id);
        return success();
    }
}
