package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.vo.RecommendLendVO;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.LendItemMapper;
import com.example.demo.mapper.LendMapper;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.RecommendService;
import com.example.demo.service.strategy.RecommendContext;
import com.example.demo.service.strategy.impl.ItemCfRecommendStrategy;
import com.example.demo.service.strategy.impl.RuleRecommendStrategy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 标的推荐服务实现。
 *
 * <h3>编排流程（/auth/recommend）</h3>
 * <ol>
 *   <li>取「募资中且未满标」的标的全集（SQL 层过滤，避免全表捞回应用层筛）</li>
 *   <li>硬过滤：非本人发布、未投过、可投能力（余额不足时**放宽**而非直接白屏）</li>
 *   <li>规则策略打分（P1）+ Item-CF 打分（P2）</li>
 *   <li>融合：检测到 CF 信号则 规则 0.4 + 协同 0.6；否则退化为纯规则</li>
 *   <li>多样性：同一借款人至多 1 个（不足 topN 时按分数补回）</li>
 *   <li>Redis 缓存 30 分钟（version 版本号失效，不做 KEYS 扫描）</li>
 *   <li>任何异常 → 热门榜兜底，**推荐位永远不白屏**</li>
 * </ol>
 */
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final int DEFAULT_TOP_N = 5;
    private static final int MAX_TOP_N = 20;
    private static final long CACHE_TTL_MINUTES = 30;
    private static final String VERSION_KEY = "recommend:version";
    private static final String USER_KEY_PREFIX = "recommend:user:";

    /** 融合权重 */
    private static final double W_RULE = 0.4;
    private static final double W_CF = 0.6;
    /** CF 信号阈值：归一化后最大分低于此值视为"无信号"，退化为纯规则 */
    private static final double CF_SIGNAL_THRESHOLD = 0.05;

    /** 缓存内容只含标量，无 LocalDateTime，普通 ObjectMapper 即可安全往返 */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private LendMapper lendMapper;

    @Autowired
    private LendItemMapper lendItemMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RuleRecommendStrategy ruleStrategy;

    @Autowired
    private ItemCfRecommendStrategy cfStrategy;

    // ==================== 对外：为你推荐 ====================

    @Override
    public List<RecommendLendVO> recommendForUser(Long userId, Integer topN) {
        int n = normTopN(topN);
        if (userId == null) {
            return hotFallback(null, n); // 未登录 → 热门
        }
        // 缓存按"整份榜单"存（最多 MAX_TOP_N 条），topN 只在读缓存时切片。
        // 否则 key 不带 topN 时，一次 topN=2 的请求会把 topN=6 的结果也污染成 2 条。
        List<RecommendLendVO> cached = readCache(userId, n);
        if (cached != null) {
            return cached;
        }
        try {
            List<RecommendLendVO> result = doRecommend(userId, MAX_TOP_N);
            if (!result.isEmpty()) {
                writeCache(userId, result);
            }
            if (result.isEmpty()) {
                return hotFallback(userId, n);
            }
            return result.size() > n ? new ArrayList<>(result.subList(0, n)) : result;
        } catch (Exception e) {
            log.warn("推荐计算异常，降级为热门榜. userId={}", userId, e);
            return hotFallback(userId, n);
        }
    }

    private List<RecommendLendVO> doRecommend(Long userId, int topN) {
        // 1) 募资中且未满标
        List<Lend> funding = selectFundingLends();

        // 2) 我的投资行为
        Set<Long> investedLendIds = selectInvestedLendIds(userId);
        Set<Long> investedBorrowerIds = selectInvestedBorrowerIds(investedLendIds);

        // 3) 硬过滤（先不含余额）
        List<Lend> base = funding.stream()
                .filter(l -> !userId.equals(l.getUserId()))
                .filter(l -> l.getId() != null && !investedLendIds.contains(l.getId()))
                .collect(Collectors.toList());
        if (base.isEmpty()) {
            return new ArrayList<>();
        }

        // 3.1) 可投能力过滤：余额不够的先排除；若因此导致结果为空则放宽（宁可推荐 + 打标签，也不白屏）
        BigDecimal balance = safeBalance(userId);
        List<Lend> candidates = base;
        if (balance != null) {
            List<Lend> affordable = base.stream()
                    .filter(l -> l.getLowestAmount() == null
                            || balance.compareTo(l.getLowestAmount()) >= 0)
                    .collect(Collectors.toList());
            if (!affordable.isEmpty()) {
                candidates = affordable;
            }
        }

        // 4) 上下文
        RecommendContext ctx = buildContext(userId, candidates, investedLendIds,
                investedBorrowerIds, balance);

        // 5) 两路打分
        List<RecommendLendVO> ruleList = ruleStrategy.recommend(ctx, topN * 2);
        if (ruleList.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Double> cfNorm = new HashMap<>();
        for (RecommendLendVO vo : cfStrategy.recommend(ctx, topN * 2)) {
            cfNorm.put(vo.getLend().getId(), vo.getScore().doubleValue());
        }
        double cfMax = cfNorm.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        boolean cfSignal = cfMax >= CF_SIGNAL_THRESHOLD;

        // 6) 融合
        List<RecommendLendVO> fused = new ArrayList<>(ruleList.size());
        for (RecommendLendVO vo : ruleList) {
            Long id = vo.getLend().getId();
            double ruleScore = vo.getScore().doubleValue();
            Double cfScore = cfNorm.get(id);
            double score;
            if (cfSignal && cfScore != null && cfScore > 0) {
                score = W_RULE * ruleScore + W_CF * cfScore;
                vo.setSource("FUSION");
                vo.getScoreDetail().put("协同分", scale(cfScore));
                appendTag(vo, "协同推荐");
                // 「复投」理由转化率最高，不被协同理由覆盖
                if (cfScore > ruleScore
                        && !RuleRecommendStrategy.REASON_REPEAT_BORROWER.equals(vo.getReason())) {
                    vo.setReason("投过相似标的的投资人也在投");
                }
            } else {
                score = ruleScore;
                vo.setSource("RULE");
            }
            vo.setScore(scale(score));
            fused.add(vo);
        }
        fused.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());

        // 7) 多样性
        return diversify(fused, topN);
    }

    // ==================== 对外：相似标的 ====================

    @Override
    public List<RecommendLendVO> similarTo(Long lendId, Integer topN) {
        int n = normTopN(topN);
        if (lendId == null) {
            return Collections.emptyList();
        }
        try {
            Lend target = lendMapper.selectById(lendId);
            if (target == null) {
                return Collections.emptyList();
            }
            List<Lend> candidates = selectFundingLends().stream()
                    .filter(l -> !lendId.equals(l.getId()))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            Map<Long, Set<Long>> vectors = buildItemVectors();
            List<RecommendLendVO> result = cfStrategy.similar(target, candidates, vectors, n);
            return result.isEmpty() ? hotFallback(null, n) : result;
        } catch (Exception e) {
            log.warn("相似标的计算异常，降级为热门榜. lendId={}", lendId, e);
            return hotFallback(null, n);
        }
    }

    // ==================== 缓存 ====================

    @Override
    public void evictAll() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("推荐缓存版本自增失败（不影响主流程）", e);
        }
    }

    @Override
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(userKey(userId));
        } catch (Exception e) {
            log.warn("推荐缓存清除失败（不影响主流程）. userId={}", userId, e);
        }
    }

    private String userKey(Long userId) {
        String version = "0";
        try {
            String v = redis.opsForValue().get(VERSION_KEY);
            if (v != null && !v.isEmpty()) {
                version = v;
            }
        } catch (Exception ignore) {
            // Redis 不可用时退化为无版本，缓存读失败会自然回落
        }
        return USER_KEY_PREFIX + userId + ":v" + version;
    }

    private void writeCache(Long userId, List<RecommendLendVO> result) {
        try {
            List<CachedHit> hits = new ArrayList<>(result.size());
            for (RecommendLendVO vo : result) {
                CachedHit h = new CachedHit();
                h.setId(vo.getLend().getId());
                h.setScore(vo.getScore());
                h.setReason(vo.getReason());
                h.setSource(vo.getSource());
                h.setTags(vo.getTags());
                h.setScoreDetail(vo.getScoreDetail());
                hits.add(h);
            }
            redis.opsForValue().set(userKey(userId), JSON.writeValueAsString(hits),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("推荐结果写缓存失败（不影响返回）", e);
        }
    }

    private List<RecommendLendVO> readCache(Long userId, int topN) {
        try {
            String json = redis.opsForValue().get(userKey(userId));
            if (json == null || json.isEmpty()) {
                return null;
            }
            List<CachedHit> hits = JSON.readValue(json, new TypeReference<List<CachedHit>>() {
            });
            if (hits == null || hits.isEmpty()) {
                return null;
            }
            Set<Long> ids = new LinkedHashSet<>();
            for (CachedHit h : hits) {
                if (h.getId() != null) {
                    ids.add(h.getId());
                }
            }
            if (ids.isEmpty()) {
                return null;
            }
            // 缓存只存 ID + 打分，标的本体每次回库取，避免展示过期金额/状态
            Map<Long, Lend> loadMap = new HashMap<>();
            for (Lend l : lendMapper.selectBatchIds(ids)) {
                loadMap.put(l.getId(), l);
            }
            List<RecommendLendVO> result = new ArrayList<>(hits.size());
            for (CachedHit h : hits) {
                Lend lend = loadMap.get(h.getId());
                if (lend == null) {
                    continue; // 标的已被删除/下架，跳过
                }
                RecommendLendVO vo = RecommendLendVO.of(lend, 0, h.getSource());
                vo.setScore(h.getScore() == null ? BigDecimal.ZERO : h.getScore());
                vo.setReason(h.getReason());
                vo.setTags(h.getTags() == null ? new ArrayList<>() : h.getTags());
                if (h.getScoreDetail() != null) {
                    vo.setScoreDetail(h.getScoreDetail());
                }
                result.add(vo);
            }
            if (result.isEmpty()) {
                return null;
            }
            // 同一份榜单按调用方要的条数切片
            return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
        } catch (Exception e) {
            log.warn("推荐缓存解析失败，回源计算", e);
            return null;
        }
    }

    // ==================== 数据装配 ====================

    /** 募资中且未满标；过滤下推到 SQL，不做全表捞回 */
    private List<Lend> selectFundingLends() {
        return lendMapper.selectList(Wrappers.<Lend>lambdaQuery()
                .eq(Lend::getStatus, LendStatusEnum.FUNDING.getCode())
                .apply("IFNULL(invest_amount, 0) < amount")
                .orderByDesc(Lend::getPublishDate)
                .orderByDesc(Lend::getId));
    }

    private Set<Long> selectInvestedLendIds(Long userId) {
        if (userId == null) {
            return new HashSet<>();
        }
        List<LendItem> items = lendItemMapper.selectList(Wrappers.<LendItem>lambdaQuery()
                .eq(LendItem::getInvestUserId, userId)
                .ne(LendItem::getStatus, 1) // 1=已撤标，不算有效行为
                .select(LendItem::getLendId, LendItem::getInvestAmount));
        return items.stream()
                .map(LendItem::getLendId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 投过哪些借款人（lend.user_id），用于"复投"推荐理由 */
    private Set<Long> selectInvestedBorrowerIds(Set<Long> investedLendIds) {
        if (investedLendIds == null || investedLendIds.isEmpty()) {
            return new HashSet<>();
        }
        return lendMapper.selectBatchIds(investedLendIds).stream()
                .map(Lend::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 标的 → 投资人集合（Item-CF 物品向量），只需 id 两列 */
    private Map<Long, Set<Long>> buildItemVectors() {
        List<LendItem> items = lendItemMapper.selectList(Wrappers.<LendItem>lambdaQuery()
                .ne(LendItem::getStatus, 1)
                .select(LendItem::getLendId, LendItem::getInvestUserId));
        Map<Long, Set<Long>> vectors = new HashMap<>();
        for (LendItem it : items) {
            if (it.getLendId() == null || it.getInvestUserId() == null) {
                continue;
            }
            vectors.computeIfAbsent(it.getLendId(), k -> new HashSet<>()).add(it.getInvestUserId());
        }
        return vectors;
    }

    private RecommendContext buildContext(Long userId, List<Lend> candidates,
                                          Set<Long> investedLendIds, Set<Long> investedBorrowerIds,
                                          BigDecimal balance) {
        RecommendContext ctx = new RecommendContext();
        ctx.setUserId(userId);
        ctx.setCandidates(candidates);
        ctx.setInvestedLendIds(investedLendIds);
        ctx.setInvestedBorrowerIds(investedBorrowerIds);
        ctx.setAvailableBalance(balance);
        ctx.setAvgPeriod(avgPeriod(userId));
        ctx.setLendInvestors(buildItemVectors());
        ctx.setMaxInvestNum(candidates.stream()
                .mapToInt(l -> l.getInvestNum() == null ? 0 : l.getInvestNum())
                .max().orElse(1));

        double min = candidates.stream()
                .filter(l -> l.getLendYearRate() != null)
                .mapToDouble(l -> l.getLendYearRate().doubleValue())
                .min().orElse(5.0);
        double max = candidates.stream()
                .filter(l -> l.getLendYearRate() != null)
                .mapToDouble(l -> l.getLendYearRate().doubleValue())
                .max().orElse(14.6);
        ctx.setMinRate(min);
        ctx.setMaxRate(max);
        return ctx;
    }

    /** 用户历史投资的平均期数（期数从标的取），无历史返回 null */
    private Double avgPeriod(Long userId) {
        Set<Long> invested = selectInvestedLendIds(userId);
        if (invested.isEmpty()) {
            return null;
        }
        List<Lend> lends = lendMapper.selectBatchIds(invested);
        double avg = lends.stream()
                .filter(l -> l.getPeriod() != null)
                .mapToInt(Lend::getPeriod)
                .average().orElse(Double.NaN);
        return Double.isNaN(avg) ? null : avg;
    }

    /**
     * 用户可用余额（读本地 user_account，不走银行外部调用 —— 推荐是高频读接口，不能依赖外部 IO）。
     * 无账户记录返回 null，表示"可投能力未知"，此时跳过该维度过滤与标签。
     */
    private BigDecimal safeBalance(Long userId) {
        try {
            UserAccount account = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                    .eq(UserAccount::getUserId, userId)
                    .last("LIMIT 1"));
            return account == null ? null : account.getAmount();
        } catch (Exception e) {
            log.debug("获取账户余额失败，跳过可投能力过滤. userId={}", userId, e);
            return null;
        }
    }

    // ==================== 兜底与工具 ====================

    /** 热门榜兜底：热度优先，其次剩余可投多。保证尽量非空 */
    private List<RecommendLendVO> hotFallback(Long userId, int topN) {
        List<Lend> funding;
        try {
            funding = selectFundingLends();
        } catch (Exception e) {
            log.warn("热门兜底查询失败", e);
            return Collections.emptyList();
        }
        List<Lend> pool = funding.stream()
                .filter(l -> userId == null || !userId.equals(l.getUserId()))
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            pool = funding; // 极端情况：用户自己发了所有标，仍返回，避免白屏
        }
        int maxInvestNum = Math.max(1, pool.stream()
                .mapToInt(l -> l.getInvestNum() == null ? 0 : l.getInvestNum())
                .max().orElse(1));

        List<RecommendLendVO> list = new ArrayList<>(pool.size());
        for (Lend l : pool) {
            double hot = (l.getInvestNum() == null ? 0 : l.getInvestNum()) * 1.0 / maxInvestNum;
            double remain = RecommendLendVO.progress(l).doubleValue();
            double score = 0.6 * hot + 0.4 * (1.0 - remain);
            RecommendLendVO vo = RecommendLendVO.of(l, score, "FALLBACK_HOT");
            vo.getScoreDetail().put("热度分", scale(hot));
            vo.setReason("热门标的 · " + (l.getInvestNum() == null ? 0 : l.getInvestNum()) + " 人已投");
            vo.setTags(new ArrayList<>());
            list.add(vo);
        }
        list.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());
        return list.size() > topN ? new ArrayList<>(list.subList(0, topN)) : list;
    }

    /** 同一借款人至多 1 个；不足 topN 时按分数补回，最终仍按分数排序展示 */
    private List<RecommendLendVO> diversify(List<RecommendLendVO> sorted, int topN) {
        Set<Long> seenBorrower = new HashSet<>();
        List<RecommendLendVO> picked = new ArrayList<>();
        List<RecommendLendVO> overflow = new ArrayList<>();
        for (RecommendLendVO vo : sorted) {
            Long borrower = vo.getLend().getUserId();
            if (borrower == null || seenBorrower.add(borrower)) {
                picked.add(vo);
            } else {
                overflow.add(vo);
            }
        }
        if (picked.size() < topN) {
            for (RecommendLendVO vo : overflow) {
                if (picked.size() >= topN) {
                    break;
                }
                picked.add(vo);
            }
        }
        picked.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());
        return picked.size() > topN ? new ArrayList<>(picked.subList(0, topN)) : picked;
    }

    private static void appendTag(RecommendLendVO vo, String tag) {
        if (vo.getTags() == null) {
            vo.setTags(new ArrayList<>());
        }
        if (!vo.getTags().contains(tag)) {
            vo.getTags().add(tag);
        }
    }

    private static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static int normTopN(Integer topN) {
        if (topN == null || topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }

    /** 缓存载体：只存标量，可被普通 ObjectMapper 无损往返 */
    @Data
    public static class CachedHit {
        private Long id;
        private BigDecimal score;
        private String reason;
        private String source;
        private List<String> tags;
        private Map<String, BigDecimal> scoreDetail;
    }
}
