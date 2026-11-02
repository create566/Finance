<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { MenuItem } from '@/config/menu'
import { menuList, activeMenuOf, openedMenuOf, assertMenuRoutes } from '@/config/menu'
import { logout as apiLogout, getUserInfo } from '@/api/user'
import { revokeEntry } from '@/utils/entryGuard'
import ChatBubble from '@/components/ChatBubble.vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const isCollapse = ref(false)
/** 详情页回落到其列表页高亮，避免"进入详情后侧栏没有任何高亮" */
const activeMenu = computed(() => activeMenuOf(route.path))
const userInfo = ref<any>(null)

/** 当前路由所属的一级菜单（由 config/menu.ts 推导，不再在此重复遍历） */
const openedMenus = computed<string[]>(() => openedMenuOf(route.path))

/**
 * 面包屑：由菜单结构 + 路由 meta 生成，不再写死「首页」。
 * 层级为「首页 / 一级域 / 当前页 / 详情页」，详情页自动补末级。
 */
const breadcrumbs = computed<{ path: string; title: string }[]>(() => {
  const items: { path: string; title: string }[] = [{ path: '/home', title: '首页' }]
  const active = activeMenu.value

  for (const group of menuList) {
    const leaves = group.children?.length ? group.children : [group]
    const hit = leaves.find((leaf) => leaf.path === active)
    if (!hit) continue
    // 无子级的一级项 hit === group，只入一次，否则面包屑会出现「认证中心 / 认证中心」
    if (group.path !== '/home') items.push({ path: group.path, title: group.title })
    if (hit.path !== group.path && hit.path !== '/home') {
      items.push({ path: hit.path, title: hit.title })
    }
    break
  }

  // 详情页等不在菜单中的页面，补上自身标题
  const selfTitle = route.meta?.title as string | undefined
  if (selfTitle && !items.some((item) => item.title === selfTitle)) {
    items.push({ path: route.path, title: selfTitle })
  }
  return items
})

const openMenus = ref<string[]>([])
const handleOpenChange = (opened: string[]) => {
  openMenus.value = opened
}

const IconComp = (name: string) => {
  return (ElementPlusIconsVue as Record<string, unknown>)[name]
}

function selectMenu(item: MenuItem) {
  if (item.children && item.children.length > 0) {
    router.push(item.children[0].path)
  } else {
    router.push(item.path)
  }
}

async function fetchUserInfo() {
  try {
    userInfo.value = await getUserInfo()
  } catch {}
}

function handleCommand(command: string) {
  if (command === 'logout') {
    doLogout()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

function doLogout() {
  ElMessageBox.confirm('确认退出登录吗？', '提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await apiLogout()
      } catch {}
      document.cookie = 'user_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      localStorage.removeItem('userType')
      // 吊销入口通行证：下次必须重新从登录页进入
      revokeEntry()
      ElMessage.success('已退出登录')
      router.push('/login')
    })
    .catch(() => {})
}

onMounted(() => {
  // 启动期校验菜单与路由是否漂移（不一致会 console.warn，避免点菜单进空白页）
  assertMenuRoutes(router)
  fetchUserInfo()
})
</script>

<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse">旺旺信贷</span>
        <span v-else>旺</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        :open-menus="openMenus"
        @open-change="handleOpenChange"
        :unique-opened="true"
        :collapse="isCollapse"
        background-color="#151a30"
        text-color="#a8b0c8"
        active-text-color="#ffffff"
        class="side-menu"
        router
      >
        <template v-for="menu in menuList" :key="menu.path">
          <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.path">
            <template #title>
              <component :is="IconComp(menu.icon)" v-if="menu.icon" />
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path" @click="selectMenu(menu)">
            <component :is="IconComp(menu.icon)" v-if="menu.icon" />
            <template #title>{{ menu.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/" class="layout-breadcrumb">
            <el-breadcrumb-item v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
              <span :class="{ 'crumb-current': index === breadcrumbs.length - 1 }">{{ crumb.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">{{ userInfo?.nickName || userInfo?.mobile || '用户' }}</span>
              <el-tag
                v-if="userInfo?.userType"
                :type="userInfo.userType === 1 ? 'primary' : 'warning'"
                size="small"
                style="margin-left: 6px"
              >
                {{ userInfo.userType === 1 ? '投资人' : '借款人' }}
              </el-tag>
              <el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="danger" plain size="small" @click="doLogout" class="logout-btn">
            <el-icon><SwitchButton /></el-icon>退出
          </el-button>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>

  <ChatBubble />
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

/* ---------- 侧栏 ---------- */
.layout-aside {
  background-color: var(--sidebar-bg);
  transition: width var(--transition-base);
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--sidebar-text-active);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  background: var(--sidebar-bg-deep);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.side-menu {
  border-right: none;
  font-size: var(--font-sm);
}

/* 图标尺寸对齐（Element Plus 图标默认 1em，深色底上易挤） */
.side-menu :deep(.el-sub-menu__title > svg),
.side-menu :deep(.el-sub-menu__title > .el-icon > svg),
.side-menu :deep(.el-menu-item > svg),
.side-menu :deep(.el-menu-item > .el-icon > svg) {
  width: 16px !important;
  height: 16px !important;
  display: inline-block;
  vertical-align: middle;
  overflow: visible;
}
.side-menu :deep(.el-sub-menu__title .el-icon),
.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 16px !important;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
/* 说明：菜单文字/激活态色由全局 style.css 的 .el-menu-item 系列统一接管
   （激活态为品牌渐变 --gradient-brand）。此处不再覆盖 is-active，
   否则旧的 #1890ff 会因 scoped 选择器权重更高而压过品牌色。 */

/* ---------- 顶栏 ---------- */
.layout-header {
  height: 56px;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-light);
  box-shadow: var(--shadow-xs);
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color var(--transition-fast);
}
.collapse-btn:hover {
  color: var(--color-primary);
}
.layout-breadcrumb {
  font-size: var(--font-sm);
}
.layout-breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--text-secondary);
  font-weight: 400;
}
.layout-breadcrumb :deep(.crumb-current) {
  color: var(--text-primary);
  font-weight: 600;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  transition: background var(--transition-fast);
}
.user-info:hover {
  background: var(--bg-hover);
}
.username {
  font-size: var(--font-base);
  color: var(--text-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logout-btn {
  font-size: var(--font-sm);
}

/* ---------- 内容区 ---------- */
.layout-main {
  background: var(--bg-page);
  padding: 20px;
  overflow-y: auto;
}
/* 路由过渡 .fade-* 已在全局 style.css 定义（含位移），此处不再重复 */
</style>