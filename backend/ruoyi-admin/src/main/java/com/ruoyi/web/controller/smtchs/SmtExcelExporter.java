package com.ruoyi.web.controller.smtchs;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Excel 双 Sheet 导出工具类。
 *
 * <p>若依自带的 {@code ExcelUtil} 单次只能写一个 Sheet，双 Sheet
 * （明细表 + 按人汇总表）需直接使用 EasyExcel 原生 API 实现。</p>
 *
 * @author ruoyi
 */
public class SmtExcelExporter
{
    /** Excel 内容类型 */
    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 明细表 Sheet 名称 */
    private static final String DETAIL_SHEET_NAME = "明细表";

    /** 按人汇总表 Sheet 名称 */
    private static final String SUMMARY_SHEET_NAME = "按人汇总表";

    private SmtExcelExporter()
    {
    }

    /**
     * 导出双 Sheet 报表：明细表 + 按人汇总表。
     *
     * @param response    响应对象
     * @param fileName    文件名（不含扩展名，中文由 URLEncoder 编码）
     * @param detailList  明细行集合
     * @param summaryList 按人汇总行集合
     * @throws IOException 写出失败
     */
    public static void export(HttpServletResponse response, String fileName,
                              List<RecordDetailExcelVO> detailList,
                              List<EmployeeSummaryExcelVO> summaryList) throws IOException
    {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded + ".xlsx");

        ExcelWriter writer = null;
        try
        {
            writer = EasyExcel.write(response.getOutputStream()).build();

            WriteSheet detailSheet = EasyExcel.writerSheet(0, DETAIL_SHEET_NAME)
                    .head(RecordDetailExcelVO.class).build();
            writer.write(detailList, detailSheet);

            WriteSheet summarySheet = EasyExcel.writerSheet(1, SUMMARY_SHEET_NAME)
                    .head(EmployeeSummaryExcelVO.class).build();
            writer.write(summaryList, summarySheet);
        }
        finally
        {
            if (writer != null)
            {
                writer.finish();
            }
        }
    }
}
