package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.system.service.ISysUserService;
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
import java.util.Set;

/**
 * 员工认证控制器（LOG-02 / LOG-04）。
 *
 * <p>登录成功后签发<b>若依真实 JWT</b>（{@link TokenService#createToken}），
 * 前端携带 {@code Authorization: Bearer <token>} 即可通过
 * {@code JwtAuthenticationTokenFilter} 与 {@code @PreAuthorize} 校验。
 * 因此员工的 {@code smt_employee.user_id} 必须关联到 {@code sys_user}。</p>
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

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    /**
     * 账号密码登录（LOG-02）。
     *
     * <p>停用校验、锁定校验与失败计数均在 Service 内完成。
     * 登录入参含明文密码，故关闭请求参数落库，避免密码进入操作日志。</p>
     *
     * @param dto 登录入参
     * @return 含 {@code token} 与 {@code user} 的登录结果
     */
    @Anonymous
    @Log(title = "员工登录", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginDTO dto)
    {
        SmtEmployee employee = employeeService.login(dto.getAccount(), dto.getPassword());

        // 签发若依真实 JWT；未关联 sys_user 时退化为仅返回用户信息（便于首次联调）
        String token = issueToken(employee);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", toUserView(employee));
        return AjaxResult.success("操作成功", data);
    }

    /**
     * 为员工签发若依 JWT。
     *
     * <p>员工通过 {@code smt_employee.user_id} 关联 {@code sys_user}，
     * 这里复用若依的用户 + 权限装配出 {@link LoginUser}，再由 {@link TokenService} 生成 token。
     * 若未关联（user_id 为空或 sys_user 不存在），返回空串——此时前端只能停留在无权限页面，
     * 部署时应按 DEPLOY.md「关联员工与若依用户」完成绑定。</p>
     *
     * @param employee 已通过密码校验的员工
     * @return JWT 字符串；无法签发时返回空串
     */
    private String issueToken(SmtEmployee employee)
    {
        Long userId = employee.getUserId();
        if (userId == null)
        {
            log.warn("员工 {} 未关联 sys_user，无法签发 JWT", employee.getAccount());
            return "";
        }

        SysUser user = sysUserService.selectUserById(userId);
        if (user == null)
        {
            log.warn("员工 {} 的 user_id={} 在 sys_user 中不存在，无法签发 JWT",
                    employee.getAccount(), userId);
            return "";
        }

        Set<String> permissions = permissionService.getMenuPermission(user);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUser(user);
        loginUser.setPermissions(permissions);

        // 记录登录信息（用于若依「在线用户」与登录日志）
        loginUser.setIpaddr(IpUtils.getIpAddr());
        loginUser.setLoginLocation("内网IP");

        return tokenService.createToken(loginUser);
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
