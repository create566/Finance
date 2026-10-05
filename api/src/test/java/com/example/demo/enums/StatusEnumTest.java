package com.example.demo.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态枚举"字典稳定性"测试。
 *
 * <p>{@link LendStatusEnum} 与 {@link ContractStatusEnum} 都是**纯字典 + fromCode 反查**，
 * 没有内建流转校验 —— 状态合法性由各业务方法自己写前置 if
 * （如 {@code offline()} 的"只有待发布/募资中才能下架"、{@code failLend()} 的"只有募资中才能流标"），
 * 那部分由 {@code LendOfflineClosureTest} 覆盖。</p>
 *
 * <p>这里钉的是字典本身：**数值稳定性**。这些 code 直接落库、前端也按数字判断，
 * 任何重排都会静默错位（比如把 OFFLINE 从 4 挪到 3，历史数据全变"已逾期"）。</p>
 */
@DisplayName("状态枚举字典稳定性测试")
class StatusEnumTest {

    // ==================== LendStatusEnum ====================

    @Test
    @DisplayName("标的 8 个状态码数值固定，不得重排（历史数据与前端均按数字判断）")
    void lendStatus_codesAreFrozen() {
        assertEquals(0, LendStatusEnum.PENDING.getCode(), "待发布");
        assertEquals(1, LendStatusEnum.FUNDING.getCode(), "募资中");
        assertEquals(2, LendStatusEnum.COMPLETED.getCode(), "已满标待放款");
        assertEquals(3, LendStatusEnum.OVERDUE.getCode(), "已逾期");
        assertEquals(4, LendStatusEnum.OFFLINE.getCode(), "已下架");
        assertEquals(5, LendStatusEnum.REPAYING.getCode(), "还款中");
        assertEquals(6, LendStatusEnum.SETTLED.getCode(), "已完结");
        assertEquals(7, LendStatusEnum.FAILED.getCode(), "已流标");
        assertEquals(8, LendStatusEnum.values().length, "状态总数应为 8");
    }

    @Test
    @DisplayName("标的 fromCode 能正确反查全部状态（往返一致）")
    void lendStatus_fromCodeRoundTrip() {
        for (LendStatusEnum e : LendStatusEnum.values()) {
            assertEquals(e, LendStatusEnum.fromCode(e.getCode()), "反查失败: code=" + e.getCode());
            assertNotNull(e.getDesc(), "必须有中文描述");
        }
    }

    @Test
    @DisplayName("标的 fromCode 未知码 → IllegalArgumentException，且消息带出原始码便于排查")
    void lendStatus_unknownCode_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LendStatusEnum.fromCode(99));

        assertTrue(ex.getMessage().contains("99"), "异常消息应带出非法码，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("标的 fromCode(null) → 抛 IllegalArgumentException 而非 NPE（调用方只 catch 前者）")
    void lendStatus_nullCode_throwsIllegalArgumentNotNpe() {
        // status.code.equals(code) 对 null 是安全的（false），因此最终抛的是 IllegalArgumentException。
        // 这个行为必须保持：若改成 code.equals(status.code) 会先 NPE，调用方 catch 不到。
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LendStatusEnum.fromCode(null));

        assertTrue(ex.getMessage().contains("未知标的状态码"), "实际：" + ex.getMessage());
    }

    // ==================== ContractStatusEnum ====================

    @Test
    @DisplayName("合同 3 个状态码数值固定（下架/流标批量作废依赖 PENDING=0）")
    void contractStatus_codesAreFrozen() {
        assertEquals(0, ContractStatusEnum.PENDING.getCode(), "待签署");
        assertEquals(1, ContractStatusEnum.SIGNED.getCode(), "已签署");
        assertEquals(2, ContractStatusEnum.REVOKED.getCode(), "已作废");
        assertEquals(3, ContractStatusEnum.values().length, "状态总数应为 3");
    }

    @Test
    @DisplayName("合同 fromCode 往返一致")
    void contractStatus_fromCodeRoundTrip() {
        for (ContractStatusEnum e : ContractStatusEnum.values()) {
            assertEquals(e, ContractStatusEnum.fromCode(e.getCode()), "反查失败: code=" + e.getCode());
            assertNotNull(e.getDesc());
        }
    }

    @Test
    @DisplayName("⚠️ 两个同类枚举失败语义不同：Lend 抛异常，Contract 返回 null（调用方必须区别对待）")
    void enumFromCodeFailureSemanticsDiffer() {
        // 这是一个真实的踩坑点：两者签名相同、长得一样，但查不到时行为相反。
        // Lend:     throw IllegalArgumentException  → 不 catch 就 500
        // Contract: return null                     → 直接 .getDesc() 就 NPE
        // 钉住现状，将来若有人统一它们，这个测试会立刻失败提醒。
        assertThrows(IllegalArgumentException.class, () -> LendStatusEnum.fromCode(99),
                "LendStatusEnum.fromCode 未知码应抛异常");
        assertNull(ContractStatusEnum.fromCode(99),
                "ContractStatusEnum.fromCode 未知码返回 null —— 调用方必须判空");
        assertNull(ContractStatusEnum.fromCode(null),
                "ContractStatusEnum.fromCode(null) 同样返回 null，不抛 NPE");
    }
}
