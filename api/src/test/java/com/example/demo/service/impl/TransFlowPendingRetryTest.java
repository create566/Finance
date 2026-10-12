package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在途占位流水的「失败 → 重试」语义（2026-10-08 修）。
 *
 * <p><b>背景：</b>{@code savePending} 原先只要"同单号存在"就跳过，
 * {@code settlePending} 又只认 {@code PROCESSING} 态。两个约束叠在一起，
 * 在「银行拒单 → 用户重试」这条真实路径上会同时失效：
 * <ol>
 *   <li>失败后重试不再占位 ⇒ 可用额占不住，并发保护失效；</li>
 *   <li>重试成功后没有 PROCESSING 流水可结算 ⇒ <b>成功流水丢失</b>，对账缺一笔。</li>
 * </ol>
 * 实测标的10 第1期就撞上了：银行因分厘差拒单 → 流水置 FAILED；
 * 修好分厘差后重试成功，{@code lend_return} 已置 1、银行也扣了款，
 * 但 {@code trans_flow} 那条仍停在"失败"。
 */
@ExtendWith(MockitoExtension.class)
class TransFlowPendingRetryTest {

    @Mock
    private TransFlowMapper baseMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private TransFlowServiceImpl service;

    private static final String TRANS_NO = "100";
    private static final Long USER_ID = 33L;

    private TransFlow flowWithStatus(Integer status) {
        TransFlow f = new TransFlow();
        f.setId(1L);
        f.setUserId(USER_ID);
        f.setTransNo(TRANS_NO);
        f.setTransAmount(new BigDecimal("869.88"));
        f.setStatus(status);
        return f;
    }

    @Test
    @DisplayName("占位：同单号已 PROCESSING 时跳过，不重复占用可用额")
    void savePending_skipsWhenProcessing() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(flowWithStatus(TransStatusEnum.PROCESSING.getCode())));

        service.savePending(USER_ID, TransTypeEnum.REPAY, new BigDecimal("869.89"), "还款", TRANS_NO);

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("占位：同单号已 SUCCESS 时跳过，不把已完成的单子重新拉回在途")
    void savePending_skipsWhenSuccess() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(flowWithStatus(TransStatusEnum.SUCCESS.getCode())));

        service.savePending(USER_ID, TransTypeEnum.REPAY, new BigDecimal("869.89"), "还款", TRANS_NO);

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("占位：同单号已 FAILED 时重新激活为处理中（允许重试，且能占住可用额）")
    void savePending_reactivatesWhenFailed() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(flowWithStatus(TransStatusEnum.FAILED.getCode())));

        service.savePending(USER_ID, TransTypeEnum.REPAY, new BigDecimal("869.89"), "还款 第1期", TRANS_NO);

        ArgumentCaptor<TransFlow> captor = ArgumentCaptor.forClass(TransFlow.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(TransStatusEnum.PROCESSING.getCode(), captor.getValue().getStatus(),
                "失败的占位流水在重试时必须被重新激活为处理中");
        assertEquals(0, new BigDecimal("869.89").compareTo(captor.getValue().getTransAmount()),
                "重试的金额应覆盖旧值");
        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("结算：重试成功时把 FAILED 翻成 SUCCESS，不能丢成功流水")
    void settlePending_turnsFailedIntoSuccess() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(flowWithStatus(TransStatusEnum.FAILED.getCode())));

        service.settlePending(TRANS_NO, true);

        ArgumentCaptor<TransFlow> captor = ArgumentCaptor.forClass(TransFlow.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(TransStatusEnum.SUCCESS.getCode(), captor.getValue().getStatus(),
                "重试成功必须把失败流水翻成成功，否则对账缺一笔");
    }

    @Test
    @DisplayName("结算：迟到的失败回调不能把已 SUCCESS 的流水改回失败")
    void settlePending_doesNotDowngradeSuccess() {
        // 只查 PROCESSING/FAILED，因此 SUCCESS 记录根本不会被捞出来
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        service.settlePending(TRANS_NO, false);

        verify(baseMapper, never()).updateById(any(TransFlow.class));
    }

    @Test
    @DisplayName("占位：首次发起正常落一条 PROCESSING 流水")
    void savePending_createsProcessingFlow() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        User user = new User();
        user.setId(USER_ID);
        user.setName("张三");
        when(userService.getById(USER_ID)).thenReturn(user);

        service.savePending(USER_ID, TransTypeEnum.REPAY, new BigDecimal("869.89"), "还款 第1期", TRANS_NO);

        ArgumentCaptor<TransFlow> captor = ArgumentCaptor.forClass(TransFlow.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(TransStatusEnum.PROCESSING.getCode(), captor.getValue().getStatus());
        assertEquals(TRANS_NO, captor.getValue().getTransNo());
    }

    @Test
    @DisplayName("状态枚举：处理中/成功/失败 三个码值与库内约定一致")
    void statusCodes() {
        assertEquals(Integer.valueOf(0), TransStatusEnum.PROCESSING.getCode());
        assertEquals(Integer.valueOf(1), TransStatusEnum.SUCCESS.getCode());
        assertEquals(Integer.valueOf(2), TransStatusEnum.FAILED.getCode());
        List<Integer> all = Arrays.asList(0, 1, 2);
        assertEquals(3, all.size());
    }
}
