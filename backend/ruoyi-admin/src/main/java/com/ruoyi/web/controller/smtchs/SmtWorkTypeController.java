package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.domain.SmtWorkType;
import com.ruoyi.smtchs.service.ISmtWorkTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作类型控制器（CFG-02）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/worktype")
public class SmtWorkTypeController extends BaseController
{
    @Autowired
    private ISmtWorkTypeService workTypeService;

    /**
     * 查询工作类型列表。
     *
     * @param query 查询条件
     * @return 分页后的工作类型列表
     */
    @PreAuthorize("@ss.hasPermi('smtchs:worktype:list')")
    @GetMapping("/list")
    public TableDataInfo list(SmtWorkType query)
    {
        startPage();
        return getDataTable(workTypeService.selectWorkTypeList(query));
    }

    /**
     * 查询全部启用中的工作类型（员工端下拉选项，无需管理权限）。
     *
     * @return 启用中的工作类型集合
     */
    @GetMapping("/enabled")
    public AjaxResult enabled()
    {
        return AjaxResult.success("操作成功", workTypeService.selectEnabledWorkTypes());
    }

    /**
     * 新增工作类型。
     *
     * @param workType 工作类型
     * @return 新增后的工作类型
     */
    @PreAuthorize("@ss.hasPermi('smtchs:worktype:add')")
    @Log(title = "工作类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SmtWorkType workType)
    {
        return AjaxResult.success("操作成功", workTypeService.createWorkType(workType));
    }

    /**
     * 修改工作类型。
     *
     * @param workType 工作类型
     * @return 修改后的工作类型
     */
    @PreAuthorize("@ss.hasPermi('smtchs:worktype:edit')")
    @Log(title = "工作类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SmtWorkType workType)
    {
        return AjaxResult.success("操作成功", workTypeService.updateWorkType(workType));
    }

    /**
     * 删除工作类型。
     *
     * <p>已被服务记录引用时 Service 改为停用，此处据返回值给出提示。</p>
     *
     * @param id 工作类型ID
     * @return 成功结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:worktype:remove')")
    @Log(title = "工作类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        // deleteWorkType 返回 true 表示"因被记录引用改为停用"
        boolean disabled = workTypeService.deleteWorkType(id);
        return AjaxResult.success(
                disabled ? "该类型已被服务记录引用，已改为停用" : "删除成功",
                Collections.singletonMap("disabled", disabled));
    }
}
