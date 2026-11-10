package com.ruoyi.smtchs.mapper;

import com.ruoyi.smtchs.domain.SmtEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 员工档案 Mapper 接口。
 *
 * @author ruoyi
 */
@Mapper
public interface SmtEmployeeMapper
{
    /**
     * 查询员工档案列表。
     *
     * @param query 查询条件
     * @return 员工档案集合
     */
    List<SmtEmployee> selectEmployeeList(SmtEmployee query);

    /**
     * 根据主键查询员工档案。
     *
     * @param id 主键
     * @return 员工档案
     */
    SmtEmployee selectEmployeeById(Long id);

    /**
     * 新增员工档案。
     *
     * @param employee 员工档案
     * @return 影响行数
     */
    int insertEmployee(SmtEmployee employee);

    /**
     * 修改员工档案。
     *
     * @param employee 员工档案
     * @return 影响行数
     */
    int updateEmployee(SmtEmployee employee);

    /**
     * 根据主键逻辑删除员工档案。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteEmployeeById(Long id);

    /**
     * 根据登录账号查询员工（登录校验用）。
     *
     * @param account 登录账号
     * @return 员工档案
     */
    SmtEmployee selectByAccount(String account);

    /**
     * 更新连续登录失败次数与锁定截止时间。
     *
     * @param id            主键
     * @param attempts      连续失败次数
     * @param lockedUntil   锁定截止时间，为 null 表示不锁定
     * @return 影响行数
     */
    int updateLoginFailure(@Param("id") Long id,
                           @Param("failedAttempts") int attempts,
                           @Param("lockedUntil") Date lockedUntil);

    /**
     * 重置连续登录失败次数并解除锁定。
     *
     * @param id 主键
     * @return 影响行数
     */
    int resetLoginFailure(Long id);

    /**
     * 更新员工对应的 sys_user 密码密文。
     *
     * <p>密码不落在 smt_employee 表，而是复用若依的 sys_user.password，
     * 通过 smt_employee.user_id 关联定位。</p>
     *
     * @param id       员工主键
     * @param password 已加密的密码密文
     * @return 影响行数，0 表示该员工未关联 sys_user
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
