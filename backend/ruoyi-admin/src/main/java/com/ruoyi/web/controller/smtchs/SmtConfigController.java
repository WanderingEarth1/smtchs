package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.domain.SmtSysConfig;
import com.ruoyi.smtchs.service.ISmtConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计费配置控制器（CFG-04）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/config")
public class SmtConfigController extends BaseController
{
    @Autowired
    private ISmtConfigService configService;

    /**
     * 查询计费配置。
     *
     * @return 计费配置
     */
    @GetMapping
    public AjaxResult getInfo()
    {
        return AjaxResult.success("操作成功", configService.getConfig());
    }

    /**
     * 更新计费配置。
     *
     * @param config 计费配置
     * @return 更新后的计费配置
     */
    @PreAuthorize("@ss.hasPermi('smtchs:config:edit')")
    @Log(title = "计费配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SmtSysConfig config)
    {
        return AjaxResult.success("操作成功", configService.updateConfig(config));
    }
}
