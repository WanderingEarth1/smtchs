package com.ruoyi.web.controller.smtchs;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 双 Sheet Excel 导出器（基于 Apache POI 原生 API）。
 *
 * <p>若依自带的 {@code ExcelUtil} 单次只能写一个 Sheet，本类直接操作
 * {@link XSSFWorkbook} 完成「明细表 + 按人汇总表」的双 Sheet 导出。</p>
 *
 * @author ruoyi
 */
public class SmtExcelExporter
{
    /** 明细表表头（顺序与 {@link RecordDetailExcelVO} 字段声明顺序一致） */
    private static final String[] DETAIL_HEADERS = {
            "流水号", "员工", "日期", "工作类型", "计费模式", "开始时间", "结束时间",
            "正常时长h", "加班时长h", "地点", "定位", "工作内容摘要", "照片链接",
            "状态", "是否补录", "单价", "金额"
    };

    /** 明细表列宽（字符数） */
    private static final int[] DETAIL_WIDTHS = {
            18, 10, 12, 12, 10, 10, 10, 12, 12, 24, 22, 40, 14, 10, 10, 10, 12
    };

    /** 汇总表表头 */
    private static final String[] SUMMARY_HEADERS = {
            "员工", "次数合计", "正常时长合计h", "加班时长合计h", "按次金额", "按时金额", "提成合计"
    };

    /** 汇总表列宽 */
    private static final int[] SUMMARY_WIDTHS = { 12, 12, 16, 16, 14, 14, 14 };

    private SmtExcelExporter()
    {
    }

    /**
     * 导出双 Sheet 报表。
     *
     * @param response    响应
     * @param fileName    文件名（不含扩展名）
     * @param detailList  明细行
     * @param summaryList 按人汇总行
     * @throws IOException IO 异常
     */
    public static void export(HttpServletResponse response, String fileName,
                              List<RecordDetailExcelVO> detailList,
                              List<EmployeeSummaryExcelVO> summaryList) throws IOException
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encoded + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook())
        {
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle moneyStyle = buildMoneyStyle(workbook);

            writeDetailSheet(workbook, headerStyle, moneyStyle, detailList);
            writeSummarySheet(workbook, headerStyle, moneyStyle, summaryList);

            try (OutputStream out = response.getOutputStream())
            {
                workbook.write(out);
                out.flush();
            }
        }
    }

    /** 明细 Sheet */
    private static void writeDetailSheet(Workbook workbook, CellStyle headerStyle,
                                         CellStyle moneyStyle, List<RecordDetailExcelVO> list)
    {
        Sheet sheet = workbook.createSheet("明细表");
        applyWidths(sheet, DETAIL_WIDTHS);

        Row header = sheet.createRow(0);
        for (int i = 0; i < DETAIL_HEADERS.length; i++)
        {
            Cell cell = header.createCell(i);
            cell.setCellValue(DETAIL_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        if (list != null)
        {
            for (RecordDetailExcelVO vo : list)
            {
                Row row = sheet.createRow(rowIndex++);
                setText(row, 0, vo.getSerialNo());
                setText(row, 1, vo.getEmployeeName());
                setText(row, 2, vo.getServiceDate());
                setText(row, 3, vo.getWorkTypeName());
                setText(row, 4, vo.getBillingMode());
                setText(row, 5, vo.getStartTime());
                setText(row, 6, vo.getEndTime());
                setNumber(row, 7, vo.getNormalHours(), moneyStyle);
                setNumber(row, 8, vo.getOvertimeHours(), moneyStyle);
                setText(row, 9, vo.getLocation());
                setText(row, 10, vo.getGeoText());
                setText(row, 11, vo.getContentSummary());
                setText(row, 12, vo.getPhotoLinks());
                setText(row, 13, vo.getStatusText());
                setText(row, 14, vo.getBackfillText());
                setNumber(row, 15, vo.getUnitPrice(), moneyStyle);
                setNumber(row, 16, vo.getAmount(), moneyStyle);
            }
        }

        sheet.createFreezePane(0, 1);
    }

    /** 按人汇总 Sheet */
    private static void writeSummarySheet(Workbook workbook, CellStyle headerStyle,
                                          CellStyle moneyStyle, List<EmployeeSummaryExcelVO> list)
    {
        Sheet sheet = workbook.createSheet("按人汇总表");
        applyWidths(sheet, SUMMARY_WIDTHS);

        Row header = sheet.createRow(0);
        for (int i = 0; i < SUMMARY_HEADERS.length; i++)
        {
            Cell cell = header.createCell(i);
            cell.setCellValue(SUMMARY_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        if (list != null)
        {
            for (EmployeeSummaryExcelVO vo : list)
            {
                Row row = sheet.createRow(rowIndex++);
                setText(row, 0, vo.getEmployeeName());
                setNumber(row, 1, vo.getCountTotal() == null
                        ? null : BigDecimal.valueOf(vo.getCountTotal()), moneyStyle);
                setNumber(row, 2, vo.getNormalHoursTotal(), moneyStyle);
                setNumber(row, 3, vo.getOvertimeHoursTotal(), moneyStyle);
                setNumber(row, 4, vo.getByCountAmount(), moneyStyle);
                setNumber(row, 5, vo.getByHourAmount(), moneyStyle);
                setNumber(row, 6, vo.getTotalAmount(), moneyStyle);
            }
        }

        sheet.createFreezePane(0, 1);
    }

    /** 表头样式：灰底加粗居中 */
    private static CellStyle buildHeaderStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addThinBorder(style);
        return style;
    }

    /** 数字样式：两位小数 */
    private static CellStyle buildMoneyStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        addThinBorder(style);
        return style;
    }

    private static void addThinBorder(CellStyle style)
    {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void applyWidths(Sheet sheet, int[] widths)
    {
        for (int i = 0; i < widths.length; i++)
        {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private static void setText(Row row, int col, String value)
    {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private static void setNumber(Row row, int col, BigDecimal value, CellStyle style)
    {
        Cell cell = row.createCell(col);
        if (value == null)
        {
            cell.setCellValue("");
        }
        else
        {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }
}
