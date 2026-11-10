package com.ruoyi.smtchs.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.smtchs.billing.BillingMode;
import com.ruoyi.smtchs.domain.SmtWorkRecord;
import com.ruoyi.smtchs.domain.SmtWorkType;
import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import com.ruoyi.smtchs.mapper.SmtWorkTypeMapper;
import com.ruoyi.smtchs.service.ISmtWorkTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作类型服务实现（CFG-02）。
 *
 * @author ruoyi
 */
@Service
public class SmtWorkTypeServiceImpl implements ISmtWorkTypeService
{
    private static final Logger log = LoggerFactory.getLogger(SmtWorkTypeServiceImpl.class);

    /** 类型名称最大长度 */
    private static final int NAME_MAX_LENGTH = 20;

    /** 启用标记 */
    private static final Integer ENABLED = 1;

    /** 停用标记 */
    private static final Integer DISABLED = 0;

    @Autowired
    private SmtWorkTypeMapper workTypeMapper;

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    @Override
    public List<SmtWorkType> selectWorkTypeList(SmtWorkType query)
    {
        return workTypeMapper.selectWorkTypeList(query == null ? new SmtWorkType() : query);
    }

    @Override
    public List<SmtWorkType> selectEnabledWorkTypes()
    {
        return workTypeMapper.selectEnabledWorkTypes();
    }

    @Override
    public SmtWorkType selectWorkTypeById(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("工作类型ID不能为空");
        }
        return workTypeMapper.selectWorkTypeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtWorkType createWorkType(SmtWorkType workType)
    {
        if (workType == null)
        {
            throw new ServiceException("工作类型不能为空");
        }

        String name = trimToNull(workType.getName());
        if (name == null)
        {
            throw new ServiceException("请填写类型名称");
        }
        if (name.length() > NAME_MAX_LENGTH)
        {
            throw new ServiceException("类型名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }

        String billingMode = trimToNull(workType.getBillingMode());
        if (billingMode == null)
        {
            throw new ServiceException("请选择计费模式");
        }
        if (!isValidBillingMode(billingMode))
        {
            throw new ServiceException("计费模式取值非法");
        }

        if (existsName(name, null))
        {
            throw new ServiceException("类型「" + name + "」已存在");
        }

        workType.setName(name);
        workType.setBillingMode(billingMode);
        workType.setEnabled(workType.getEnabled() == null ? ENABLED : workType.getEnabled());
        if (workType.getSort() == null)
        {
            workType.setSort(workTypeMapper.selectWorkTypeList(new SmtWorkType()).size() + 1);
        }
        workType.setCreateBy(SecurityUtils.getUsername());

        workTypeMapper.insertWorkType(workType);
        log.info("新增工作类型 id={}，名称={}，计费模式={}",
                workType.getId(), name, billingMode);
        return workTypeMapper.selectWorkTypeById(workType.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmtWorkType updateWorkType(SmtWorkType workType)
    {
        if (workType == null || workType.getId() == null)
        {
            throw new ServiceException("工作类型ID不能为空");
        }

        SmtWorkType current = workTypeMapper.selectWorkTypeById(workType.getId());
        if (current == null)
        {
            throw new ServiceException("工作类型不存在");
        }

        if (workType.getName() != null)
        {
            String name = trimToNull(workType.getName());
            if (name == null)
            {
                throw new ServiceException("类型名称不能为空");
            }
            if (name.length() > NAME_MAX_LENGTH)
            {
                throw new ServiceException("类型名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
            }
            if (existsName(name, workType.getId()))
            {
                throw new ServiceException("类型「" + name + "」已存在");
            }
            workType.setName(name);
        }

        if (workType.getBillingMode() != null)
        {
            String billingMode = trimToNull(workType.getBillingMode());
            if (billingMode == null || !isValidBillingMode(billingMode))
            {
                throw new ServiceException("计费模式取值非法");
            }
            // 计费模式变更不影响历史记录：历史记录已固化 snap_billing_mode 快照
            workType.setBillingMode(billingMode);
        }

        workType.setUpdateBy(SecurityUtils.getUsername());
        workTypeMapper.updateWorkType(workType);
        log.info("修改工作类型 id={}", workType.getId());
        return workTypeMapper.selectWorkTypeById(workType.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWorkType(Long id)
    {
        if (id == null)
        {
            throw new ServiceException("工作类型ID不能为空");
        }

        SmtWorkType current = workTypeMapper.selectWorkTypeById(id);
        if (current == null)
        {
            throw new ServiceException("工作类型不存在");
        }

        // 已被服务记录引用时改为停用，避免历史记录失去类型名称
        SmtWorkRecord query = new SmtWorkRecord();
        query.setWorkTypeId(id);
        List<SmtWorkRecord> used = recordMapper.selectRecordList(query);
        if (used != null && !used.isEmpty())
        {
            SmtWorkType patch = new SmtWorkType();
            patch.setId(id);
            patch.setEnabled(DISABLED);
            patch.setUpdateBy(SecurityUtils.getUsername());
            workTypeMapper.updateWorkType(patch);
            log.info("工作类型 id={} 已被 {} 条记录引用，改为停用", id, used.size());
            return true;
        }

        workTypeMapper.deleteWorkTypeById(id);
        log.info("删除工作类型 id={}", id);
        return false;
    }

    /**
     * 判断类型名称是否已被占用。
     *
     * @param name      类型名称
     * @param excludeId 需要排除的主键（修改场景传自身ID），新增传 null
     */
    private boolean existsName(String name, Long excludeId)
    {
        List<SmtWorkType> all = workTypeMapper.selectWorkTypeList(new SmtWorkType());
        if (all == null)
        {
            return false;
        }
        for (SmtWorkType item : all)
        {
            if (excludeId != null && excludeId.equals(item.getId()))
            {
                continue;
            }
            if (name.equals(item.getName()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验计费模式取值是否合法。
     */
    private boolean isValidBillingMode(String mode)
    {
        for (BillingMode item : BillingMode.values())
        {
            if (item.name().equals(mode))
            {
                return true;
            }
        }
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
