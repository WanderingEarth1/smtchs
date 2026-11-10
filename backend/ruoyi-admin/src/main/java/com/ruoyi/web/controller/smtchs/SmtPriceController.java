package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.smtchs.domain.SmtPriceRule;
import com.ruoyi.smtchs.service.ISmtPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单价矩阵控制器（CFG-03）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/price")
public class SmtPriceController extends BaseController
{
    @Autowired
    private ISmtPriceService priceService;

    /**
     * 查询完整单价矩阵（员工 × 工作类型，含姓名与类型名称）。
     *
     * @return 单价规则集合
     */
    @PreAuthorize("@ss.hasPermi('smtchs:price:list')")
    @GetMapping("/matrix")
    public AjaxResult matrix()
    {
        return AjaxResult.success("操作成功", priceService.selectMatrix());
    }

    /**
     * 设置单价：存在则更新，不存在则新增。
     *
     * <p>入参为单条规则，主键不参与保存，由「员工 + 工作类型」定位。</p>
     *
     * @param rule 单价规则，需含 {@code employeeId} / {@code workTypeId} / {@code price}
     * @return 保存后的单价规则
     */
    @PreAuthorize("@ss.hasPermi('smtchs:price:edit')")
    @Log(title = "单价矩阵", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SmtPriceRule rule)
    {
        return AjaxResult.success("操作成功",
                priceService.setUnitPrice(rule.getEmployeeId(), rule.getWorkTypeId(), rule.getPrice()));
    }

    /**
     * 按行快速复制：把某员工的全部单价复制给其他员工。
     *
     * @param body 请求体，含 {@code fromEmployeeId} 与 {@code toEmployeeIds}
     * @return 含 {@code count}（实际写入条数）的结果
     */
    @PreAuthorize("@ss.hasPermi('smtchs:price:edit')")
    @Log(title = "单价矩阵", businessType = BusinessType.INSERT)
    @PostMapping("/copy")
    public AjaxResult copy(@RequestBody Map<String, Object> body)
    {
        Long fromEmployeeId = toLong(body.get("fromEmployeeId"));
        if (fromEmployeeId == null)
        {
            return error("请选择源员工");
        }

        List<Long> toEmployeeIds = toLongList(body.get("toEmployeeIds"));
        if (toEmployeeIds.isEmpty())
        {
            return error("请选择目标员工");
        }

        int count = priceService.copyPriceRow(fromEmployeeId, toEmployeeIds);
        return AjaxResult.success("复制成功", count);
    }

    /**
     * 把 JSON 反序列化出的数字（Integer / Long / String）统一转成 {@link Long}。
     *
     * @param value 原始值
     * @return 转换结果，无法识别时返回 {@code null}
     */
    private Long toLong(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isEmpty())
        {
            try
            {
                return Long.valueOf((String) value);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        return null;
    }

    /**
     * 把 JSON 数组统一转成 {@link Long} 集合，忽略无法识别的元素。
     *
     * @param value 原始值
     * @return 员工ID集合，非集合入参返回空集合
     */
    private List<Long> toLongList(Object value)
    {
        List<Long> ids = new ArrayList<>();
        if (!(value instanceof List))
        {
            return ids;
        }
        for (Object item : (List<?>) value)
        {
            Long id = toLong(item);
            if (id != null)
            {
                ids.add(id);
            }
        }
        return ids;
    }
}
