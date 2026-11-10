package com.ruoyi.smtchs.util;

import com.ruoyi.smtchs.mapper.SmtWorkRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 流水号生成器。
 *
 * <p>格式：{@code SR{yyyyMM}{4位序号}}，例如 {@code SR2026110019}。
 * 序号按「当月已有最大流水号 + 1」递增，与前端 {@code nextSerialNo} 规则一致。</p>
 *
 * <p>注意：{@code synchronized} 只能保证单实例内串行，多实例部署时仍需依赖
 * {@code smt_work_record.uk_serial_no} 唯一键兜底。</p>
 *
 * @author ruoyi
 */
@Component
public class SerialNoGenerator
{
    /** 流水号前缀 */
    private static final String PREFIX = "SR";

    /** 月份格式 */
    private static final String MONTH_PATTERN = "yyyyMM";

    /** 序号位数 */
    private static final int SEQ_WIDTH = 4;

    @Autowired
    private SmtWorkRecordMapper recordMapper;

    /**
     * 生成下一个流水号。
     *
     * @return 形如 SR2026110019 的流水号
     */
    public synchronized String next()
    {
        String prefix = PREFIX + new SimpleDateFormat(MONTH_PATTERN).format(new Date());
        String max = recordMapper.selectMaxSerialNo(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length())
        {
            try
            {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            }
            catch (NumberFormatException ignored)
            {
                seq = 1;
            }
        }
        return prefix + String.format("%0" + SEQ_WIDTH + "d", seq);
    }
}
