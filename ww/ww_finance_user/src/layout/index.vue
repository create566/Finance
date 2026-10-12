<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { MenuItem } from '@/config/menu'
import { menuList } from '@/config/menu'
import { logout as apiLogout, getUserInfo } from '@/api/user'
import { revokeEntry } from '@/utils/entryGuard'
import ChatBubble from '@/components/ChatBubble.vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)
const userInfo = ref<any>(null)

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

onMounted(fetchUserInfo)
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

    <el-container>
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
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logout-btn {
  font-size: 13px;
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