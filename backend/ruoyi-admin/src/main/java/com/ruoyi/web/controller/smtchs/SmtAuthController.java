package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.domain.SmtEmployee;
import com.ruoyi.smtchs.dto.ChangePasswordDTO;
import com.ruoyi.smtchs.dto.LoginDTO;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 员工认证控制器（LOG-02 / LOG-04）。
 *
 * <p>原型阶段 token 仅用于前端会话占位，未接入若依的 JWT 过滤器链；
 * 真正的身份识别仍由若依 Spring Security 会话完成。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/auth")
public class SmtAuthController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SmtAuthController.class);

    /** 角色缺省值：未关联 sys_user 角色时按普通员工处理 */
    private static final String DEFAULT_ROLE = "EMPLOYEE";

    @Autowired
    private ISmtEmployeeService employeeService;

    /**
     * 账号密码登录（LOG-02）。
     *
     * <p>停用校验、锁定校验与失败计数均在 Service 内完成。
     * 登录入参含明文密码，故关闭请求参数落库，避免密码进入操作日志。</p>
     *
     * @param dto 登录入参
     * @return 含 {@code token} 与 {@code user} 的登录结果
     */
    @Log(title = "员工登录", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginDTO dto)
    {
        SmtEmployee employee = employeeService.login(dto.getAccount(), dto.getPassword());

        // 原型阶段使用随机串占位，后续接入 JWT 时替换此处
        String token = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", toUserView(employee));
        return AjaxResult.success("操作成功", data);
    }

    /**
     * 退出登录。
     *
     * <p>会话清理由若依 Spring Security 过滤器负责，此处仅记录操作人。</p>
     *
     * @return 成功结果
     */
    @PostMapping("/logout")
    public AjaxResult logout()
    {
        log.info("员工退出登录：{}", SecurityUtils.getUsername());
        return success();
    }

    /**
     * 取当前登录用户信息。
     *
     * @return 当前登录员工
     */
    @GetMapping("/info")
    public AjaxResult info()
    {
        return AjaxResult.success("操作成功", toUserView(employeeService.getCurrentEmployee()));
    }

    /**
     * 修改本人密码（LOG-04）。
     *
     * <p>入参含原密码与新密码，关闭请求参数落库。</p>
     *
     * @param dto 原密码与新密码
     * @return 成功结果
     */
    @Log(title = "个人密码", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/password")
    public AjaxResult changePassword(@RequestBody ChangePasswordDTO dto)
    {
        employeeService.changePassword(dto);
        return success();
    }

    /**
     * 组装前端所需的用户视图，仅暴露 id / name / account / role 四个字段。
     *
     * @param employee 员工档案
     * @return 用户视图
     */
    private Map<String, Object> toUserView(SmtEmployee employee)
    {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", employee.getId());
        user.put("name", employee.getName());
        user.put("account", employee.getAccount());
        user.put("role", employee.getRole() == null ? DEFAULT_ROLE : employee.getRole());
        return user;
    }
}
