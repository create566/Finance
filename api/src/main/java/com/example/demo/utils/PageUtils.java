package com.example.demo.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数归一化（所有列表接口的统一入口）。
 *
 * <p><b>为什么必须有</b>：此前 18 处分页对象都直接 {@code new Page<>(pageNum, pageSize)}，
 * 而 {@code pageSize} 完全来自请求参数、没有任何上限 —— 一个
 * {@code pageSize=1000000} 的请求就会让数据库把整张表捞出来再序列化，
 * {@code trans_flow} 这类只会变大的流水表尤其致命；这是成本极低的 DoS 面。
 * {@code pageNum} 同理：{@code pageNum=10000000} 会变成
 * {@code LIMIT 100000000, 10}，MySQL 必须先扫过这 1 亿行才能取到 10 条。
 *
 * <p><b>上限取值</b>：页长钳到 100，因为前端 el-pagination 的备选页长最大就是 100，
 * 钳到 100 不会影响任何现有页面；页码钳到 10000，远超本项目任何真实数据量。
 *
 * <p>用法：把 {@code new Page<>(pageNum, pageSize)} 换成 {@code PageUtils.of(pageNum, pageSize)}。
 */
public final class PageUtils {

    /** 单页最大条数 */
    public static final long MAX_PAGE_SIZE = 100L;
    /** 最大页码（防 OFFSET 深翻页扫描） */
    public static final long MAX_PAGE_NUM = 10000L;
    /** 页长缺省值，与各接口 {@code @RequestParam(defaultValue = "10")} 保持一致 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    private PageUtils() {
    }

    /**
     * {@code Integer} 入参版本：覆盖绝大多数接口（{@code @RequestParam Integer pageNum/pageSize}）。
     *
     * <p>刻意不提供 {@code of(Long, Long)} 重载：三个以上的 {@code of} 重载会让
     * "一个 int 字面量 + 一个 Integer 变量"这种混搭写法变成编译期歧义。
     */
    public static <T> Page<T> of(Integer pageNum, Integer pageSize) {
        return new Page<>(
                normalizePageNum(pageNum == null ? 1L : pageNum.longValue()),
                normalizePageSize(pageSize == null ? DEFAULT_PAGE_SIZE : pageSize.longValue()));
    }

    /** 原始 long 重载：给 {@code pageByUser(long page, long limit)} 这类签名用。 */
    public static <T> Page<T> of(long pageNum, long pageSize) {
        return new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
    }

    public static long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : Math.min(pageNum, MAX_PAGE_NUM);
    }

    public static long normalizePageSize(long pageSize) {
        // 非法页长（<=0）退回缺省值而不是 1：避免调用方传 0 时"看起来一切正常但只有一条数据"
        return pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
