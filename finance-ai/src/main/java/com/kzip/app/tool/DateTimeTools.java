package com.kzip.app.tool;

import org.springframework.ai.tool.annotation.Tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间相关工具，供 ChatClient 在对话中按需调用
 */
public class DateTimeTools {

    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzzz");

    /**
     * 当用户询问"现在几点了"、"当前时间"、"今天几号"、"星期几"等日期时间问题时调用此工具。
     * 返回中华人民共和国标准时间（北京时间，UTC+8 / Asia/Shanghai）。
     *
     * @return 格式化的日期时间字符串，例如 "2026-08-20 15:30:45 中国标准时间"
     */
    @Tool(name = "getCurrentDateTime", description = """
            当用户询问当前时间、现在几点、今天日期、今天星期几、什么时候、几点几分等日期时间相关问题时必须调用此工具。
            不要凭空编造时间，一定要调用本工具获取真实时间后再回答。
            返回中华人民共和国标准时间（北京时间，UTC+8，Asia/Shanghai时区），
            格式如"2026-08-20 15:30:45 中国标准时间"。
            """)
    public String getCurrentDateTime() {
        System.out.println("调用了getCurrentDateTime");
        return ZonedDateTime.now(ZONE_CN).format(FMT);
    }
}
