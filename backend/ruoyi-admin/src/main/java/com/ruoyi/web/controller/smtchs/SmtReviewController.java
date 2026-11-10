package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.dto.RevisionDTO;
import com.ruoyi.smtchs.service.ISmtReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务记录审核控制器（AUD-01 ~ AUD-05）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/review")
public class SmtReviewController extends BaseController
{
    @Autowired
    private ISmtReviewService reviewService;

    /**
     * 确认单条记录（AUD-02）。
     *
     * @param id 记录ID
     * @return 成功结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:review:approve')")
    @Log(title = "记录审核", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{id}")
    public AjaxResult approve(@PathVariable Long id)
    {
        reviewService.approveRecord(id);
        return success();
    }

    /**
     * 批量确认（AUD-03）。
     *
     * @param recordIds 记录ID集合
     * @return 含 {@code success} 与 {@code failed} 的统计结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:review:approve')")
    @Log(title = "记录审核", businessType = BusinessType.UPDATE)
    @PutMapping("/approve-batch")
    public AjaxResult approveBatch(@RequestBody List<Long> recordIds)
    {
        Map<String, Integer> result = reviewService.approveRecords(recordIds);
        return AjaxResult.success("操作成功", result);
    }

    /**
     * 驳回记录（AUD-02：驳回原因必填）。
     *
     * @param id   记录ID
     * @param body 请求体，取 {@code reason} 字段
     * @return 成功结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:review:reject')")
    @Log(title = "记录审核", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{id}")
    public AjaxResult reject(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        reviewService.rejectRecord(id, body.get("reason"));
        return success();
    }

    /**
     * 修正已确认记录（AUD-04：留痕）。
     *
     * @param id  记录ID
     * @param dto 修正内容与说明
     * @return 修正后的服务记录
     */
    @PreAuthorize("@ss.hasPermi('smtchs:review:revise')")
    @Log(title = "记录修正", businessType = BusinessType.UPDATE)
    @PutMapping("/revise/{id}")
    public AjaxResult revise(@PathVariable Long id, @RequestBody RevisionDTO dto)
    {
        return AjaxResult.success("操作成功", reviewService.reviseRecord(id, dto));
    }

    /**
     * 查询指定记录的修正痕迹。
     *
     * @param id 记录ID
     * @return 修正痕迹集合
     */
    @GetMapping("/revisions/{id}")
    public AjaxResult revisions(@PathVariable Long id)
    {
        return AjaxResult.success("操作成功", reviewService.selectRevisions(id));
    }
}
