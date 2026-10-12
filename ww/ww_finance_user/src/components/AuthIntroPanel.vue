<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getPlatformStatistics, type PlatformStatistics } from '@/api/statistics'

/**
 * 登录 / 注册页左侧介绍面板（简约大气版）。
 *
 * 版式：品牌 → 大标语 → 一句话简介 → 产品介绍（运作三步）/ 特性清单 → 底部数据/信任条。
 * 数据区（运营指标）全部来自 `GET /api/core/index/statistics` 的**实时聚合**结果；
 * 取不到（后端未部署该接口）时降级为静态信任条，**不展示任何写死的假数字**。
 */
interface Step {
  title: string
  desc: string
}
interface Feature {
  title: string
  desc: string
}

const props = withDefaults(
  defineProps<{
    /** 主标语，逐行渲染 */
    slogan: string[]
    /** 一句话产品简介 */
    desc: string
    /** 产品介绍：运作步骤（与特性清单二选一或同时使用） */
    steps?: Step[]
    /** 产品特性清单 */
    features?: Feature[]
    /** 是否尝试拉取运营数据 */
    showStats?: boolean
  }>(),
  { steps: () => [], features: () => [], showStats: true },
)

const stats = ref<PlatformStatistics | null>(null)

onMounted(async () => {
  if (props.showStats) {
    stats.value = await getPlatformStatistics()
  }
})

/** 金额按口径缩写：亿 / 万 / 元 */
function formatAmount(value?: number | null) {
  if (value == null) return '—'
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿`
  if (value >= 10000) return `${(value / 10000).toFixed(2)} 万`
  return value.toFixed(2)
}

const statItems = computed(() => {
  const s = stats.value
  if (!s) return []
  return [
    { value: `¥ ${formatAmount(s.matchedAmount)}`, label: '累计撮合成交' },
    { value: `${s.userCount}`, label: '注册用户' },
    {
      value: s.avgYearRate > 0 ? `${s.avgYearRate.toFixed(2)}%` : '—',
      label: '标的平均年化',
    },
    { value: `${s.publishingCount}`, label: '募资中标的' },
  ]
})

/** 利率区间：只有真实存在标的（且有跨度）时才展示 */
const rateRange = computed(() => {
  const s = stats.value
  if (!s || s.minYearRate == null || s.maxYearRate == null) return ''
  if (s.minYearRate === s.maxYearRate) return `${s.minYearRate.toFixed(2)}%`
  return `${s.minYearRate.toFixed(2)}% ~ ${s.maxYearRate.toFixed(2)}%`
})
</script>

<template>
  <div class="intro-panel">
    <div class="brand">
      <div class="logo-icon">旺</div>
      <span class="brand-name">旺旺信贷</span>
    </div>

    <div class="hero">
      <h2>
        <template v-for="(line, i) in props.slogan" :key="i">
          {{ line }}<br v-if="i < props.slogan.length - 1" />
        </template>
      </h2>
      <p class="desc">{{ props.desc }}</p>
    </div>

    <!-- 产品介绍：平台如何运作 -->
    <section v-if="props.steps.length" class="block">
      <p class="block-title">产品介绍 · 平台如何运作</p>
      <ol class="steps">
        <li v-for="(s, i) in props.steps" :key="s.title">
          <span class="num">{{ String(i + 1).padStart(2, '0') }}</span>
          <div class="step-body">
            <p class="step-title">{{ s.title }}</p>
            <p class="step-desc">{{ s.desc }}</p>
          </div>
        </li>
      </ol>
    </section>

    <!-- 产品特性 -->
    <section v-if="props.features.length" class="block">
      <p class="block-title">平台特性</p>
      <ul class="feature-list">
        <li v-for="item in props.features" :key="item.title">
          <span class="dot" />
          <div>
            <p class="feature-title">{{ item.title }}</p>
            <p class="feature-desc">{{ item.desc }}</p>
          </div>
        </li>
      </ul>
    </section>

    <!-- 底部：运营数据（实时），取不到则降级为信任条 -->
    <div class="foot">
      <template v-if="statItems.length">
        <div class="stat-head">
          <span class="stat-title">平台运营数据</span>
          <span class="stat-tip">实时聚合 · 取自业务库</span>
        </div>
        <div class="stat-row">
          <div v-for="s in statItems" :key="s.label" class="stat-item">
            <p class="stat-value">{{ s.value }}</p>
            <p class="stat-label">{{ s.label }}</p>
          </div>
        </div>
        <p v-if="rateRange" class="stat-extra">当前标的年化区间 {{ rateRange }}</p>
      </template>
      <div v-else class="trust-chips">
        <span>✓ 银行资金存管</span>
        <span class="sep" />
        <span>✓ 信息透明披露</span>
        <span class="sep" />
        <span>✓ 全流程留痕</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.intro-panel {
  position: relative;
  height: 100%;
  padding: 44px 64px 36px;
  background: linear-gradient(165deg, #f7faff 0%, #eff5fd 55%, #e6effb 100%);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
}
/* 一枚极淡的装饰圆，增加空气感而不抢戏 */
.intro-panel::before {
  content: '';
  position: absolute;
  right: -140px;
  bottom: -180px;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(91, 134, 229, 0.09) 0%, rgba(91, 134, 229, 0) 68%);
  pointer-events: none;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 56px;
}
.logo-icon {
  width: 40px;
  height: 40px;
  line-height: 40px;
  text-align: center;
  background: linear-gradient(135deg, #36d1dc, #5b86e5);
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  border-radius: 10px;
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #16324f;
  letter-spacing: 1px;
}
.hero h2 {
  font-size: 34px;
  line-height: 1.5;
  font-weight: 700;
  color: #0f2b4c;
  letter-spacing: 1px;
  margin: 0 0 18px;
}
.desc {
  font-size: 14px;
  line-height: 2;
  color: #64748b;
  margin: 0;
  max-width: 430px;
}
.block {
  margin-top: 44px;
}
.block-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 3px;
  color: #8aa0b8;
  margin: 0 0 18px;
}
/* 运作三步：极简数字时间线 */
.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.steps li {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.num {
  flex-shrink: 0;
  width: 34px;
  font-size: 19px;
  font-weight: 700;
  color: #5b86e5;
  opacity: 0.85;
  line-height: 1.4;
}
.step-title {
  font-size: 14.5px;
  font-weight: 600;
  color: #24466b;
  margin: 0 0 2px;
}
.step-desc {
  font-size: 12.5px;
  color: #7e93aa;
  margin: 0;
}
/* 特性清单 */
.feature-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.feature-list li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  margin-top: 7px;
  border-radius: 50%;
  background: linear-gradient(135deg, #36d1dc, #5b86e5);
}
.feature-title {
  font-size: 14.5px;
  font-weight: 600;
  color: #24466b;
  margin: 0 0 2px;
}
.feature-desc {
  font-size: 12.5px;
  color: #7e93aa;
  margin: 0;
}
/* 底部数据 / 信任条 */
.foot {
  margin-top: auto;
  padding-top: 26px;
}
.stat-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}
.stat-title {
  font-size: 13px;
  font-weight: 600;
  color: #24466b;
}
.stat-tip {
  font-size: 11px;
  color: #93a7bd;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  padding-top: 16px;
  border-top: 1px solid rgba(91, 134, 229, 0.18);
}
.stat-value {
  font-size: 19px;
  font-weight: 700;
  color: #2f6fd2;
  margin: 0 0 3px;
  white-space: nowrap;
}
.stat-label {
  font-size: 12px;
  color: #8296ad;
  margin: 0;
}
.stat-extra {
  margin: 12px 0 0;
  font-size: 12px;
  color: #7e93aa;
}
.trust-chips {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(91, 134, 229, 0.18);
  font-size: 12.5px;
  color: #5b7492;
}
.trust-chips .sep {
  width: 1px;
  height: 12px;
  background: rgba(91, 134, 229, 0.25);
}
@media (max-width: 1100px) {
  .intro-panel {
    padding: 32px 32px 28px;
  }
  .hero h2 {
    font-size: 26px;
  }
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .brand {
    margin-bottom: 32px;
  }
}
</style>
