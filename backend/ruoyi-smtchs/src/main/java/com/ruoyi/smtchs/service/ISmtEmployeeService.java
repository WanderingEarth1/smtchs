package com.ruoyi.smtchs.service;

import com.ruoyi.smtchs.domain.SmtEmployee;
import com.ruoyi.smtchs.dto.ChangePasswordDTO;

import java.util.List;

/**
 * 员工档案与登录服务接口（LOG-01 ~ LOG-06 / CFG-01）。
 *
 * @author ruoyi
 */
public interface ISmtEmployeeService
{
    /**
     * 账号密码登录（LOG-02）。
     *
     * <p>包含停用校验、锁定校验、连续失败计数与锁定（LOG-05）。
     * 登录成功后重置失败计数。</p>
     *
     * @param account  登录账号
     * @param password 明文密码
     * @return 登录成功的员工档案（不含密码密文）
     */
    SmtEmployee login(String account, String password);

    /**
     * 修改本人密码（LOG-04）。
     *
     * @param dto 原密码与新密码
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 管理员重置指定员工密码（LOG-04）。
     *
     * @param employeeId  员工ID
     * @param newPassword 新密码
     */
    void resetPassword(Long employeeId, String newPassword);

    /**
     * 查询员工列表。
     *
     * @param query 查询条件
     * @return 员工集合
     */
    List<SmtEmployee> selectEmployeeList(SmtEmployee query);

    /**
     * 根据主键查询员工。
     *
     * @param id 主键
     * @return 员工档案
     */
    SmtEmployee selectEmployeeById(Long id);

    /**
     * 取当前登录员工档案。
     *
     * <p>通过 {@code SecurityUtils.getUserId()} 关联 {@code smt_employee.user_id} 定位，
     * 未关联时抛出业务异常。</p>
     *
     * @return 当前登录员工
     */
    SmtEmployee getCurrentEmployee();

    /**
     * 取当前登录员工ID。
     *
     * @return 员工ID
     */
    Long getCurrentEmployeeId();

    /**
     * 新增员工（CFG-01）。
     *
     * @param employee 员工档案，{@code password} 字段承载初始密码
     * @return 新增后的员工档案
     */
    SmtEmployee createEmployee(SmtEmployee employee);

    /**
     * 修改员工（CFG-01）。
     *
     * @param employee 员工档案
     * @return 修改后的员工档案
     */
    SmtEmployee updateEmployee(SmtEmployee employee);

    /**
     * 删除员工（CFG-01 / LOG-06）。
     *
     * <p>若该员工已有服务记录，则改为停用以避免历史统计断链。</p>
     *
     * @param id 员工ID
     * @return true 表示因存在历史记录而改为停用，false 表示已逻辑删除
     */
    boolean deleteEmployee(Long id);
}
