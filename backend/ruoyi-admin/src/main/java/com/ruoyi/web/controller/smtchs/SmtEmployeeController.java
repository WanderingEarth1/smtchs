package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.domain.SmtEmployee;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
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

import java.util.Map;

/**
 * 员工管理控制器（CFG-01 / LOG-04 / LOG-06）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/employee")
public class SmtEmployeeController extends BaseController
{
    @Autowired
    private ISmtEmployeeService employeeService;

    /**
     * 查询员工列表。
     *
     * @param query 查询条件
     * @return 分页后的员工列表
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:list')")
    @GetMapping("/list")
    public TableDataInfo list(SmtEmployee query)
    {
        startPage();
        return getDataTable(employeeService.selectEmployeeList(query));
    }

    /**
     * 查询员工详情。
     *
     * @param id 员工ID
     * @return 员工档案
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return AjaxResult.success("操作成功", employeeService.selectEmployeeById(id));
    }

    /**
     * 新增员工（CFG-01）。
     *
     * <p>入参 {@code password} 字段承载初始密码，故关闭请求参数落库。</p>
     *
     * @param employee 员工档案
     * @return 新增后的员工档案
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:add')")
    @Log(title = "员工管理", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult add(@RequestBody SmtEmployee employee)
    {
        return AjaxResult.success("操作成功", employeeService.createEmployee(employee));
    }

    /**
     * 修改员工（CFG-01）。
     *
     * @param employee 员工档案
     * @return 修改后的员工档案
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:edit')")
    @Log(title = "员工管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SmtEmployee employee)
    {
        return AjaxResult.success("操作成功", employeeService.updateEmployee(employee));
    }

    /**
     * 删除员工（CFG-01 / LOG-06）。
     *
     * <p>已有服务记录时 Service 改为停用，此处据返回值给出提示。</p>
     *
     * @param id 员工ID
     * @return 成功结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:remove')")
    @Log(title = "员工管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        // deleteEmployee 返回 true 表示"因有历史记录改为停用"，
        // 前端据 data.disabled 区分提示文案
        boolean disabled = employeeService.deleteEmployee(id);
        return AjaxResult.success(
                disabled ? "该员工已有服务记录，已改为停用" : "删除成功",
                Collections.singletonMap("disabled", disabled));
    }

    /**
     * 管理员重置指定员工密码（LOG-04）。
     *
     * <p>入参含明文密码，关闭请求参数落库。</p>
     *
     * @param id   员工ID
     * @param body 请求体，取 {@code password} 字段
     * @return 成功结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:employee:resetPwd')")
    @Log(title = "员工密码", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/{id}/password")
    public AjaxResult resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        employeeService.resetPassword(id, body.get("password"));
        return success();
    }
}
