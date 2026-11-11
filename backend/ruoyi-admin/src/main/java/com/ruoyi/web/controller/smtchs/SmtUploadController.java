package com.ruoyi.web.controller.smtchs;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.file.FileUploadUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 照片上传控制器（REC-02）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/smtchs/upload")
public class SmtUploadController extends BaseController
{
    /** 单张照片大小上限：300KB */
    private static final long MAX_PHOTO_SIZE = 300 * 1024L;

    /** 允许的图片扩展名（含点，用于入参校验） */
    private static final String[] ALLOWED_EXTENSIONS = { ".jpg", ".jpeg", ".png", ".webp" };

    /**
     * 允许的图片扩展名（不含点，供 {@link FileUploadUtils} 校验）。
     *
     * <p>若依默认白名单不含 webp，故此处显式传入，避免 webp 被拒。</p>
     */
    private static final String[] ALLOWED_EXTENSIONS_NO_DOT = { "jpg", "jpeg", "png", "webp" };

    /**
     * 上传服务记录照片。
     *
     * @param file 照片文件
     * @return 含 {@code url} 与 {@code size} 的结果
     * @throws IOException 保存文件失败
     */
    @Log(title = "照片上传", businessType = BusinessType.INSERT)
    @PostMapping("/photo")
    public AjaxResult uploadPhoto(@RequestParam("file") MultipartFile file)
            throws IOException, InvalidExtensionException
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择要上传的照片");
        }
        // 单张 ≤300KB
        if (file.getSize() > MAX_PHOTO_SIZE)
        {
            return error("照片大小不能超过 300KB");
        }
        // 校验图片类型
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension.toLowerCase()))
        {
            return error("仅支持 jpg / jpeg / png / webp 格式");
        }

        // 用若依的 FileUploadUtils 保存
        String url = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, ALLOWED_EXTENSIONS_NO_DOT);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("size", file.getSize());
        return AjaxResult.success("上传成功", data);
    }
}
