package com.example.demo.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页参数归一化测试。
 *
 * <p>背景：列表接口的 pageSize/pageNum 直接来自请求参数且没有上限，
 * {@code pageSize=1000000} 会让数据库捞全表，{@code pageNum=10000000} 会变成
 * {@code LIMIT 100000000, 10} 让 MySQL 白扫一亿行。这里把边界钉死。
 *
 * <p>用 {@code sizeOf/currentOf} 这类「参数类型固定」的小助手调用，而不是在断言里直接写
 * {@code of(1, null)}：{@code of(Integer,Integer)} 与 {@code of(long,long)} 并存时，
 * "int 字面量 + Integer 变量"的混搭写法是编译期歧义，助手方法里参数类型是确定的。
 */
@DisplayName("分页参数归一化测试")
class PageUtilsTest {

    private static long sizeOf(Integer pageNum, Integer pageSize) {
        return PageUtils.of(pageNum, pageSize).getSize();
    }

    private static long currentOf(Integer pageNum, Integer pageSize) {
        return PageUtils.of(pageNum, pageSize).getCurrent();
    }

    private static long sizeOfLong(long pageNum, long pageSize) {
        return PageUtils.of(pageNum, pageSize).getSize();
    }

    @Test
    @DisplayName("正常值原样透传（不影响现有页面）")
    void normalValues_passThrough() {
        assertEquals(2L, currentOf(2, 20));
        assertEquals(20L, sizeOf(2, 20));
        // 前端 el-pagination 的最大备选页长就是 100，必须原样放行
        assertEquals(100L, sizeOf(1, 100));
    }

    @Test
    @DisplayName("超大页长被钳到 100")
    void hugePageSize_clamped() {
        assertEquals(100L, sizeOf(1, 1000000));
        assertEquals(100L, sizeOf(1, 101));
        assertEquals(PageUtils.MAX_PAGE_SIZE, sizeOf(1, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("页码被钳到 10000（防深翻页扫描）")
    void hugePageNum_clamped() {
        assertEquals(PageUtils.MAX_PAGE_NUM, currentOf(100000000, 10));
    }

    @Test
    @DisplayName("非法页长（0/负数/null）退回缺省 10，不返回 0 条")
    void illegalPageSize_fallsBackToDefault() {
        assertEquals(10L, sizeOf(1, 0), "页长 0 不能让界面看起来'正常但没数据'");
        assertEquals(10L, sizeOf(1, -5));
        assertEquals(10L, sizeOf(1, null));
    }

    @Test
    @DisplayName("非法页码（0/负数/null）退回第 1 页")
    void illegalPageNum_fallsBackToFirstPage() {
        assertEquals(1L, currentOf(0, 10));
        assertEquals(1L, currentOf(-3, 10));
        assertEquals(1L, currentOf(null, 10));
    }

    @Test
    @DisplayName("long 重载（pageByUser(long page, long limit) 这类签名）同样生效")
    void longOverload_works() {
        assertEquals(100L, sizeOfLong(1L, 999L));
        assertEquals(PageUtils.MAX_PAGE_NUM, PageUtils.of(99999999L, 10L).getCurrent());
        assertEquals(50L, sizeOfLong(3L, 50L));
    }

    @Test
    @DisplayName("返回值类型可用于泛型推断（Page<Lend> page = PageUtils.of(...)）")
    void genericInference_works() {
        Page<String> page = PageUtils.of(Integer.valueOf(1), Integer.valueOf(10));
        assertEquals(1L, page.getCurrent());
        assertEquals(10L, page.getSize());
    }
}
