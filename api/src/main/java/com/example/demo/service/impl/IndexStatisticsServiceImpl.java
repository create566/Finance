package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.User;
import com.example.demo.entity.vo.PlatformStatisticsVO;
import com.example.demo.mapper.LendMapper;
import com.example.demo.mapper.LendReturnMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.IndexStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 平台运营统计实现。
 *
 * <p><b>口径说明（全部来自业务库实时聚合，非写死的展示文案）</b>：</p>
 * <ul>
 *   <li>注册用户 / 借款人 / 投资人：{@code user} 表有效行，借款人 = borrow_auth_status=2，
 *       投资人 = user_type=1 或 invest_auth_status=2（两者可重叠，合计不等于总人数）；</li>
 *   <li>累计发标金额 = Σ lend.amount；累计撮合成交金额 = Σ lend.invest_amount；</li>
 *   <li>年化利率 / 期限：对真实标的取均值与区间；</li>
 *   <li>按时履约率 = 已按期还款期数 /（已按期还款期数 + 逾期期数），无到期记录时返回 null，
 *       <b>不臆造 100%</b>。</li>
 * </ul>
 *
 * <p>逻辑删除由实体上的 {@code @TableLogic} 保证（MyBatis-Plus 自动追加 is_deleted=0）。</p>
 */
@Slf4j
@Service
public class IndexStatisticsServiceImpl implements IndexStatisticsService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LendMapper lendMapper;

    @Autowired
    private LendReturnMapper lendReturnMapper;

    @Override
    public PlatformStatisticsVO platformStatistics() {
        PlatformStatisticsVO vo = new PlatformStatisticsVO();

        // ---------- 标的维度：一次聚合查询取回全部金额/利率/期限口径 ----------
        QueryWrapper<Lend> lendWrapper = new QueryWrapper<>();
        lendWrapper.select(
                "COUNT(*) AS lend_count",
                "IFNULL(SUM(amount), 0) AS publish_amount",
                "IFNULL(SUM(invest_amount), 0) AS matched_amount",
                "IFNULL(AVG(lend_year_rate), 0) AS avg_year_rate",
                "IFNULL(MIN(lend_year_rate), 0) AS min_year_rate",
                "IFNULL(MAX(lend_year_rate), 0) AS max_year_rate",
                "IFNULL(AVG(period), 0) AS avg_period");
        List<Map<String, Object>> lendRows = lendMapper.selectMaps(lendWrapper);
        Map<String, Object> lendRow = lendRows.isEmpty() ? null : lendRows.get(0);

        vo.setLendCount(toLong(pick(lendRow, "lend_count")));
        vo.setPublishAmount(toDecimal(pick(lendRow, "publish_amount")));
        vo.setMatchedAmount(toDecimal(pick(lendRow, "matched_amount")));
        vo.setAvgYearRate(scale2(toDecimal(pick(lendRow, "avg_year_rate"))));
        vo.setMinYearRate(scale2(toDecimal(pick(lendRow, "min_year_rate"))));
        vo.setMaxYearRate(scale2(toDecimal(pick(lendRow, "max_year_rate"))));
        vo.setAvgPeriod(scale1(toDecimal(pick(lendRow, "avg_period"))));

        // ---------- 募资中标的数量 ----------
        // 注意：MyBatis-Plus 3.4.1 的 selectCount 返回 Integer，统一用 Number 转换后再取 long，
        // 这样无论是 3.4.x(Integer) 还是 3.5.x(Long) 都不会编译不过或精度丢失。
        vo.setPublishingCount(count(lendMapper, new QueryWrapper<Lend>().eq("status", 1)));

        // ---------- 用户维度 ----------
        vo.setUserCount(count(userMapper, new QueryWrapper<User>()));
        vo.setBorrowerCount(count(userMapper, new QueryWrapper<User>().eq("borrow_auth_status", 2)));
        vo.setInvestorCount(count(userMapper, new QueryWrapper<User>()
                .and(w -> w.eq("user_type", 1).or().eq("invest_auth_status", 2))));

        // ---------- 还款履约维度 ----------
        vo.setReturnPlanCount(count(lendReturnMapper, new QueryWrapper<LendReturn>()));
        vo.setReturnedCount(count(lendReturnMapper, new QueryWrapper<LendReturn>().eq("status", 1)));
        vo.setOverdueCount(count(lendReturnMapper, new QueryWrapper<LendReturn>().eq("is_overdue", 1)));

        long settled = vo.getReturnedCount() + vo.getOverdueCount();
        if (settled > 0) {
            vo.setOnTimeRate(BigDecimal.valueOf(vo.getReturnedCount() * 100.0 / settled)
                    .setScale(2, RoundingMode.HALF_UP));
        }

        log.debug("平台运营统计: 用户={}, 标的={}, 撮合={}, 年化={}%",
                vo.getUserCount(), vo.getLendCount(), vo.getMatchedAmount(), vo.getAvgYearRate());
        return vo;
    }

    /** 计数：兼容 selectCount 在不同 MyBatis-Plus 版本下返回 Integer / Long 的差异 */
    private static long count(BaseMapper<?> mapper, Wrapper<?> wrapper) {
        Number n = (Number) mapper.selectCount((Wrapper) wrapper);
        return n == null ? 0L : n.longValue();
    }

    /** 大小写不敏感地取值：不同 MySQL 版本/驱动对别名大小写保留策略不一致 */
    private static Object pick(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
