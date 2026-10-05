<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { MenuItem } from '@/config/menu'
import { menuList } from '@/config/menu'
import { logout as apiLogout } from '@/api/login'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

// Bug#1 修复：根据当前路由自动展开父菜单
const openedMenus = computed<string[]>(() => {
  const result: string[] = []
  for (const menu of menuList) {
    if (menu.children?.some((c) => c.path === route.path)) {
      result.push(menu.path)
      break
    }
  }
  return result
})

// Bug#1 修复：点击 sub-menu 切换展开状态（和 unique-opened 配合）
const openMenus = ref<string[]>([])
const handleOpenChange = (opened: string[]) => {
  openMenus.value = opened
}

// 动态渲染图标组件
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

// Bug#2 修复：el-dropdown command 参数
function handleCommand(command: string) {
  if (command === 'logout') {
    doLogout()
  }
}

function doLogout() {
  ElMessageBox.confirm('确认退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await apiLogout()
      } catch {
        // 后端登失败也不阻碍前端退出
      }
      // 清 cookie token
      document.cookie = 'admin_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      localStorage.removeItem('username')
      ElMessage.success('已退出登录')
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse">旺旺信贷</span>
        <span v-else>旺</span>
      </div>
      <!-- Bug#1 修复：绑定 default-openeds + unique-opened -->
      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        :open-menus="openMenus"
        @open-change="handleOpenChange"
        :unique-opened="true"
        :collapse="isCollapse"
        background-color="#001529"
        text-color="#b7bdc6"
        active-text-color="#409EFF"
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

    <!-- 右侧主体 -->
    <el-container>
      <!-- 头部 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta?.title">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- Bug#2 修复：command 绑定 -->
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">admin</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}
.layout-aside {
  background-color: #001529;
  transition: width 0.28s;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background: #002140;
}
.side-menu {
  border-right: none;
  font-size: 14px;
}
/* ========= 图标尺寸锁死 ========= */
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
/* ================================ */
.side-menu :deep(.el-menu-item.is-active) {
  background-color: #1890ff !important;
  color: #fff !important;
}
.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.username {
  font-size: 14px;
  color: #333;
}
.layout-main {
  background: #f0f2f5;
  padding: 16px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>