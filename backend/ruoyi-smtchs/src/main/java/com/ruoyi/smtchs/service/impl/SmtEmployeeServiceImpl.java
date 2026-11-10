package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.domain.SmtEmployee;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.dto.ChangePasswordDTO;
import com.ruoyi.smtchs.mapper.SmtEmployeeMapper;
import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import com.ruoyi.smtchs.service.ISmtEmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 员工档案与登录服务实现（LOG-01 ~ LOG-06 / CFG-01）。
 *
 * @author ruoyi
 */
@Service
public class SmtEmployeeServiceImpl implements ISmtEmployeeService
{
    private static final Logger log = LoggerFactory.getLogger(SmtEmployeeServiceImpl.class);

    /** 连续输错锁定阈值（LOG-05） */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长：15 分钟（LOG-05） */
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000L;

    /** 一分钟的毫秒数 */
    private static final long ONE_MINUTE_MS = 60 * 1000L;

    /** 姓名最大长度 */
    private static final int NAME_MAX_LENGTH = 20;

    /** 密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 登录账号规则：3~20 位字母、数字或下划线 */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "0";

    /** 状态：停用 */
    private static final String STATUS_DISABLED = "1";

    @Autowired
    private SmtEmployeeMapper employeeMapper;

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    /**
     * {@inheritDoc}
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：本方法在密码错误分支需要「先累加失败次数再抛异常」。
     * {@link ServiceException} 属于 {@code RuntimeException}，若包在同一事务中，抛异常会让
     * 失败次数与锁定时间的写入一并回滚，导致 LOG-05 的连续输错锁定完全失效。</p>
     */
    @Override
    public SmtEmployee login(String account, String password)
    {
        String acc = trimToNull(account);
        if (acc == null)
        {
            throw new ServiceException("请输入登录账号");
        }
        if (password == null || password.isEmpty())
        {
            throw new ServiceException("请输入密码");
        }

        SmtEmployee emp = employeeMapper.selectByAccount(acc);
        if (emp == null)
        {
            log.info("登录失败：账号 {} 不存在", acc);
            throw new ServiceException("账号或密码错误");
        }

        // LOG-06：停用员工无法登录
        if (STATUS_DISABLED.equals(emp.getStatus()))
        {
            log.info("登录失败：账号 {} 已停用", acc);
            throw new ServiceException("该账号已停用，请联系管理员");
        }

        // LOG-05：锁定检查
        Date lockedUntil = emp.getLockedUntil();
        if (lockedUntil != null)
        {
            long remainMs = lockedUntil.getTime() - System.currentTimeMillis();
            if (remainMs > 0)
            {
                long mins = remainMs / ONE_MINUTE_MS + 1;
                log.info("登录失败：账号 {} 锁定中，剩余约 {} 分钟", acc, mins);
                throw new ServiceException("密码连续输错 5 次，账号已锁定，请 " + mins + " 分钟后再试");
            }
            // 锁定已过期，重置计数后继续校验
            employeeMapper.resetLoginFailure(emp.getId());
            emp.setFailedAttempts(0);
            emp.setLockedUntil(null);
        }

        // 密码校验：复用若依的 BCrypt 比对
        String encoded = emp.getPassword();
        if (encoded == null || encoded.isEmpty())
        {
            // 未关联 sys_user 或未初始化密码，一律按凭证错误处理，避免泄露账号状态
            log.warn("登录失败：账号 {} 未关联系统用户或未设置密码", acc);
            throw new ServiceException("账号或密码错误");
        }
        if (!SecurityUtils.matchesPassword(password, encoded))
        {
            int attempts = (emp.getFailedAttempts() == null ? 0 : emp.getFailedAttempts()) + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS)
            {
                Date lockUntil = new Date(System.currentTimeMillis() + LOCK_DURATION_MS);
                employeeMapper.updateLoginFailure(emp.getId(), 0, lockUntil);
                log.info("登录失败：账号 {} 连续输错 {} 次，锁定 15 分钟", acc, MAX_FAILED_ATTEMPTS);
                throw new ServiceException("密码连续输错 5 次，账号已锁定 15 分钟");
            }
            employeeMapper.updateLoginFailure(emp.getId(), attempts, null);
            log.info("登录失败：账号 {} 密码错误，剩余 {} 次机会", acc, MAX_FAILED_ATTEMPTS - attempts);
            throw new ServiceException("账号或密码错误，还可尝试 " + (MAX_FAILED_ATTEMPTS - attempts) + " 次");
        }

        // 登录成功：重置失败计数
        employeeMapper.resetLoginFailure(emp.getId());
        emp.setFailedAttempts(0);
        emp.setLockedUntil(null);
        // 密文不返回给调用方
        emp.setPassword(null);
        log.info("登录成功：账号 {}（员工ID {}）", acc, emp.getId());
        return emp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto)
    {
        if (dto == null)
        {
            throw new ServiceException("参数不能为空");
        }
        if (dto.getOldPassword() == null || dto.getOldPassword().isEmpty())
        {
            throw new ServiceException("请输入原密码");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < PASSWORD_MIN_LENGTH)
        {
            throw new ServiceException("新密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }
        if (dto.getNewPassword().equals(dto.getOldPassword()))
        {
            throw new ServiceException("新密码不能与原密码相同");
        }

        SmtEmployee emp = getCurrentEmployee();
        // getCurrentEmployee 会清空密码字段，此处按账号重新取密文用于比对
        SmtEmployee withPassword = employeeMapper.selectByAccount(emp.getAccount());
        if (withPassword == null)
        {
            throw new ServiceException("账号不存在");
        }
        if (!SecurityUtils.matchesPassword(dto.getOldPassword(), withPassword.getPassword()))
        {
            throw new ServiceException("原密码不正确");
        }
        String encoded = SecurityUtils.encryptPassword(dto.getNewPassword());
        int affected = employeeMapper.updatePassword(emp.getId(), encoded);
        if (affected == 0)
        {
            throw new ServiceException("该账号未关联系统用户，无法修改密码，请联系管理员");
        }
        log.info("员工 {} 修改本人密码成功", emp.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long employeeId, String newPassword)
    {
        if (employeeId == null)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (newPassword == null || newPassword.length() < PASSWORD_MIN_LENGTH)
        {
            throw new ServiceException("新密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }

        SmtEmployee emp = employeeMapper.selectEmployeeById(employeeId);
        if (emp == null)
        {
            throw new ServiceException("员工不存在");
        }

        String encoded = SecurityUtils.encryptPassword(newPassword);
        int affected = employeeMapper.updatePassword(employeeId, encoded);
        if (affected == 0)
        {
            throw new ServiceException("该账号未关联系统用户，无法重置密码");
        }
        // 重置密码同时解除锁定
        employeeMapper.resetLoginFailure(employeeId);
        log.info("管理员重置员工 {} 的密码", employeeId);
    }

    @Override
    public List<SmtEmployee> selectEmployeeList(SmtEmployee query)
    {
        return employeeMapper.selectEmployeeList(query == null ? new SmtEmployee() : query);
    }

    @Override
    public SmtEmployee selectEmployeeById(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("员工ID不能为空");
        }
        SmtEmployee emp = employeeMapper.selectEmployeeById(id);
        if (emp != null)
        {
            emp.setPassword(null);
        }
        return emp;
    }

    @Override
    public SmtEmployee getCurrentEmployee()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("未获取到当前登录用户");
        }

        SmtEmployee query = new SmtEmployee();
        query.setUserId(userId);
        List<SmtEmployee> list = employeeMapper.selectEmployeeList(query);
        if (list == null || list.isEmpty())
        {
            throw new ServiceException("当前登录用户未关联员工档案，请联系管理员");
        }

        SmtEmployee emp = list.get(0);
        // 需要密码密文时由调用方另行查询，此处不返回
        emp.setPassword(null);
        return emp;
    }

    @Override
    public Long getCurrentEmployeeId()
    {
        return getCurrentEmployee().getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtEmployee createEmployee(SmtEmployee employee)
    {
        if (employee == null)
        {
            throw new ServiceException("员工档案不能为空");
        }

        String name = trimToNull(employee.getName());
        if (name == null)
        {
            throw new ServiceException("请填写员工姓名");
        }
        if (name.length() > NAME_MAX_LENGTH)
        {
            throw new ServiceException("姓名不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }

        String account = trimToNull(employee.getAccount());
        if (account == null)
        {
            throw new ServiceException("请填写登录账号");
        }
        if (!ACCOUNT_PATTERN.matcher(account).matches())
        {
            throw new ServiceException("登录账号需为 3~20 位字母、数字或下划线");
        }

        String rawPassword = employee.getPassword();
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN_LENGTH)
        {
            throw new ServiceException("初始密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }

        if (employeeMapper.selectByAccount(account) != null)
        {
            throw new ServiceException("登录账号「" + account + "」已存在");
        }

        employee.setName(name);
        employee.setAccount(account);
        employee.setStatus(employee.getStatus() == null ? STATUS_ENABLED : employee.getStatus());
        employee.setFailedAttempts(0);
        employee.setLockedUntil(null);
        employee.setCreateBy(SecurityUtils.getUsername());
        // 密码不落 smt_employee 表
        employee.setPassword(null);

        employeeMapper.insertEmployee(employee);

        // 密码写入关联的 sys_user；未关联 user_id 时由管理员后续绑定
        if (employee.getUserId() != null)
        {
            employeeMapper.updatePassword(employee.getId(),
                    SecurityUtils.encryptPassword(rawPassword));
        }
        else
        {
            log.warn("新增员工 {} 未关联 sys_user，初始密码未写入，需管理员绑定后重置", employee.getId());
        }

        log.info("新增员工 id={}，姓名={}，账号={}", employee.getId(), name, account);
        return employeeMapper.selectEmployeeById(employee.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtEmployee updateEmployee(SmtEmployee employee)
    {
        if (employee == null || employee.getId() == null)
        {
            throw new ServiceException("员工ID不能为空");
        }

        SmtEmployee current = employeeMapper.selectEmployeeById(employee.getId());
        if (current == null)
        {
            throw new ServiceException("员工不存在");
        }

        if (employee.getName() != null)
        {
            String name = trimToNull(employee.getName());
            if (name == null)
            {
                throw new ServiceException("姓名不能为空");
            }
            if (name.length() > NAME_MAX_LENGTH)
            {
                throw new ServiceException("姓名不能超过 " + NAME_MAX_LENGTH + " 个字符");
            }
            employee.setName(name);
        }

        if (employee.getAccount() != null)
        {
            String account = trimToNull(employee.getAccount());
            if (account == null || !ACCOUNT_PATTERN.matcher(account).matches())
            {
                throw new ServiceException("登录账号需为 3~20 位字母、数字或下划线");
            }
            SmtEmployee dup = employeeMapper.selectByAccount(account);
            if (dup != null && !dup.getId().equals(employee.getId()))
            {
                throw new ServiceException("登录账号「" + account + "」已存在");
            }
            employee.setAccount(account);
        }

        // 密码不通过该接口修改
        employee.setPassword(null);
        employee.setUpdateBy(SecurityUtils.getUsername());
        employeeMapper.updateEmployee(employee);

        // 启用时解除锁定
        if (STATUS_ENABLED.equals(employee.getStatus()))
        {
            employeeMapper.resetLoginFailure(employee.getId());
        }

        log.info("修改员工 id={}", employee.getId());
        return employeeMapper.selectEmployeeById(employee.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEmployee(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("员工ID不能为空");
        }

        SmtEmployee current = employeeMapper.selectEmployeeById(id);
        if (current == null)
        {
            throw new ServiceException("员工不存在");
        }

        // 已有服务记录时改为停用，避免历史统计断链（LOG-06）
        SmtWorkRecord query = new SmtWorkRecord();
        query.setEmployeeId(id);
        List<SmtWorkRecord> used = recordMapper.selectRecordList(query);
        if (used != null && !used.isEmpty())
        {
            SmtEmployee patch = new SmtEmployee();
            patch.setId(id);
            patch.setStatus(STATUS_DISABLED);
            patch.setUpdateBy(SecurityUtils.getUsername());
            employeeMapper.updateEmployee(patch);
            log.info("员工 {} 已有 {} 条服务记录，改为停用", id, used.size());
            return true;
        }

        employeeMapper.deleteEmployeeById(id);
        log.info("删除员工 id={}", id);
        return false;
    }

    /**
     * 去除首尾空白，空串归一为 null。
     */
    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
