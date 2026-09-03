package com.example.demo.service.impl;

import com.example.demo.entity.BorrowInfo;
import com.example.demo.enums.LoanOrderState;
import org.springframework.stereotype.Service;


@Service
public class LoanOrderStateMachine {

    /**
     * 执行状态变更（核心入口）
     * @param order 借款订单实体
     * @param targetState 目标状态
     * @param eventSource 触发来源（用于日志，区分是“用户操作”还是“系统定时任务”）
     */
    public void changeState(BorrowInfo order, Integer targetStatecode, String eventSource) {
        Integer currentStatusCode = order.getStatus();
        LoanOrderState currentState = LoanOrderState.fromCode(currentStatusCode);
        LoanOrderState targetState = LoanOrderState.fromCode(targetStatecode);
        // 1. 校验规则：是否允许跳转
        if (!currentState.canTransitionTo(targetState)) {
            throw new IllegalStateException(
                    String.format("非法状态流转: [%s] 不允许转为 [%s]",
                            currentState.getDesc(), targetState.getDesc())
            );
        }

        // 2. 执行业务动作（根据具体目标状态，执行不同的业务逻辑）
        doBusinessAction(order, targetState);

        // 3. 更新状态
        order.setStatus(targetStatecode);

        // 4. 记录状态变更日志（教学重点：审计日志）
        System.out.println("状态变更: " + currentState.getDesc() + " -> " + targetState.getDesc()
                + ", 触发来源: " + eventSource);
    }

    /**
     * 状态变更前的业务动作绑定（教学演示）
     */
    private void doBusinessAction(BorrowInfo order, LoanOrderState targetState) {
        switch (targetState) {
            case WAIT_APPROVAL:
                // 草稿提交审核：校验资料是否完整
                System.out.println("执行【提交审核】动作：校验借款人资料完整性...");
                break;
            case APPROVED:
                // 审核通过：生成签约合同
                System.out.println("执行【审核通过】动作：生成电子合同...");
                break;
            case WAIT_SIGN:
                // 待签约：发送短信提醒借款人签约
                System.out.println("执行【待签约】动作：发送签约提醒短信...");
                break;
            case WAIT_DISBURSEMENT:
                // 待放款：调用资金方接口准备打款
                System.out.println("执行【待放款】动作：资金方额度预扣...");
                break;
            case REPAYING:
                // 放款成功：生成还款计划表
                System.out.println("执行【放款成功】动作：生成36期还款计划...");
                break;
            case COMPLETED:
                // 结清：释放抵押物或关闭合同
                System.out.println("执行【已完结】动作：关闭合同，释放风控额度...");
                break;
            case REJECTED:
                // 拒贷：释放额度，记录拒绝原因
                System.out.println("执行【拒贷】动作：释放授信额度，记录拒绝原因...");
                break;
            case OVERDUE:
                // 逾期（特别注意：此动作通常由定时任务触发，而非用户操作）
                System.out.println("执行【逾期】动作：开始计收罚息，发送催收通知...");
                break;
            default:
                break;
        }
    }

}
