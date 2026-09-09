package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.enums.BankNotifyStatusEnum;
import com.example.demo.enums.LoanOrderState;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;


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

    /**
     * 交易流水 Service 实现类
     *
     * 核心职责：
     *   1. 充值 —— 原子加余额 + 记一条流水
     *   2. 提现 —— 原子扣余额 + 记一条流水
     *   3. 查流水 —— 按用户分页查询
     *
     * 关键设计：
     *   - 充值/提现都加了 @Transactional，"改余额"和"记流水"在同一个事务里，要么都成功要么都回滚
     *   - 扣钱用 UPDATE ... WHERE amount >= ? 原子操作，不用"先查后改"，避免并发超扣
     *   - 流水只 INSERT 不 UPDATE，trans_no 用 LendNoUtils 生成，数据库有唯一索引兜底
     */
    @Log4j2
    @Service
    public static class TransFlowServiceImpl extends ServiceImpl<TransFlowMapper, TransFlow>
            implements TransFlowService {

        /** 银行回调成功状态码 */
        private static final String BANK_STATUS_SUCCESS = "1";

        /** 金额格式：正数，最多两位小数 */
        private static final String AMOUNT_REGEX = "\\d+(\\.\\d{1,2})?";

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private UserAccountService userAccountService;

        @Autowired
        private UserService userService;

        /**
         * 充值
         * 步骤：1. 查用户（提前校验）  2. 原子加钱  3. 记流水
         */
        @Override
        @Transactional(rollbackFor = Exception.class)
        public void recharge(Long userId, BigDecimal amount, String bankTransNo) {
            // 1. 参数校验
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("充值金额必须大于0");
            }

            // 2. 提前查用户，不存在直接抛异常，避免后面白做
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在，充值失败");
            }

            // 3. 原子加钱：委托 UserAccountService 处理余额变更
            userAccountService.recharge(userId, amount);

            // 4. 记流水（传入已查到的 user，不再重复查询）
            saveTransFlow(user, TransTypeEnum.RECHARGE.getCode(), TransTypeEnum.RECHARGE.getDesc(),
                    amount, "用户充值", bankTransNo);

            log.info("充值成功，userId={}, amount={}", userId, amount);
        }

        /**
         * 提现
         * 步骤：1. 查用户（提前校验）  2. 原子扣钱  3. 记流水
         */
        @Override
        @Transactional(rollbackFor = Exception.class)
        public void withdraw(Long userId, BigDecimal amount, String bankTransNo) {
            // 1. 参数校验
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("提现金额必须大于0");
            }

            // 2. 提前查用户，不存在直接抛异常
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在，提现失败");
            }

            // 3. 原子扣钱：委托 UserAccountService 处理余额变更
            userAccountService.withdraw(userId, amount);

            // 4. 记流水（传入已查到的 user，不再重复查询）
            saveTransFlow(user, 2, "提现", amount, "用户提现", bankTransNo);

            log.info("提现成功，userId={}, amount={}", userId, amount);
        }

        @Override
        public boolean existsByTransNo(String bankTransNo) {
            LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TransFlow::getTransNo, bankTransNo);
            return this.count(wrapper) > 0;
        }

        /**
         * 充值回调处理（Controller 只负责接参和应答，业务全在本方法）
         * 流程：验签 → 银行状态判断 → 金额校验 → 用户查询 → 幂等 → 入账
         */
        @Override
        public boolean handleRechargeNotify(Map<String, Object> params) {
            if (!BankSignUtil.isSignEquals(params)) {
                log.warn("充值回调验签失败，params={}", params);
                return false;
            }
            String agentUserId = (String) params.get("agentUserId");
            String amountStr = (String) params.get("amount");
            String transNo = (String) params.get("transNo");
            String status = (String) params.get("status");

            // 银行侧处理失败：无需重试，应答 success
            if (!BANK_STATUS_SUCCESS.equals(status)) {
                return true;
            }
            if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
                log.warn("充值回调金额非法，amount={}", amountStr);
                return false;
            }
            User user = userService.getByMobile(agentUserId);
            if (user == null) {
                log.warn("充值回调用户不存在，agentUserId={}", agentUserId);
                return false;
            }
            // 幂等：同一银行流水号不重复入账
            if (transNo != null && existsByTransNo(transNo)) {
                log.info("充值回调重复通知，直接确认，transNo={}", transNo);
                return true;
            }
            recharge(user.getId(), new BigDecimal(amountStr), transNo);
            return true;
        }

        /**
         * 提现回调处理
         * 流程：验签 → 银行状态判断 → 金额校验 → 用户查询 → 幂等 → 扣账
         */
        @Override
        public boolean handleWithdrawNotify(Map<String, Object> params) {
            if (!BankSignUtil.isSignEquals(params)) {
                log.warn("提现回调验签失败，params={}", params);
                return false;
            }
            String agentUserId = (String) params.get("agentUserId");
            String amountStr = (String) params.get("amount");
            String transNo = (String) params.get("transNo");
            String status = (String) params.get("status");

            if (!BankNotifyStatusEnum.SUCCESS.getCode().equals(status)) {
                return true;
            }
            if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
                log.warn("提现回调金额非法，amount={}", amountStr);
                return false;
            }
            User user = userService.getByMobile(agentUserId);
            if (user == null) {
                log.warn("提现回调用户不存在，agentUserId={}", agentUserId);
                return false;
            }
            if (transNo != null && existsByTransNo(transNo)) {
                log.info("提现回调重复通知，直接确认，transNo={}", transNo);
                return true;
            }
            withdraw(user.getId(), new BigDecimal(amountStr), transNo);
            return true;
        }

        /**
         * 查询当前用户的交易流水（分页，按创建时间倒序）
         */
        @Override
        public IPage<TransFlow> listByUser(Long userId, long page, long limit) {
            // 1. 构造分页参数
            Page<TransFlow> pageParam = new Page<>(page, limit);

            // 2. 构造查询条件：按 userId 过滤，按创建时间倒序
            LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TransFlow::getUserId, userId)
                   .orderByDesc(TransFlow::getCreateTime);

            // 3. 执行分页查询
            return baseMapper.selectPage(pageParam, wrapper);
        }

        /**
         * 私有方法：组装并保存一条交易流水
         * 充值和提现共用，避免重复代码
         * user 由调用方提前查询并传入，不在内部重复查询
         */
        private void saveTransFlow(User user, int transType, String transTypeName,
                                    BigDecimal amount, String memo, String bankTransNo) {
            // 优先用银行流水号做 trans_no（回调幂等），没有则本地生成
            String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                    ? bankTransNo : LendNoUtils.getTransNo();
            TransFlow transFlow = new TransFlow()
                    .setUserId(user.getId())
                    .setUserName(user.getName())
                    .setTransNo(transNo)
                    .setTransType(transType)
                    .setTransTypeName(transTypeName)
                    .setTransAmount(amount)
                    .setMemo(memo);
            this.save(transFlow);
        }

    }
}
