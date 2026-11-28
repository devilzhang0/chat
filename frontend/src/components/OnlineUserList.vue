<script setup lang="ts">
import {computed} from 'vue';
import {X} from 'lucide-vue-next';
import {
  getAvatarBgClass,
  getAvatarInitials,
  type ConnectionStatus,
  type OnlineUser,
} from '@/types/chat';
import {formatMessageTime} from '@/utils/formatTime';

const props = defineProps<{
  users: OnlineUser[];
  currentUser: OnlineUser | null;
  status: ConnectionStatus;
  showCloseButton?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const sortedUsers = computed(() => {
  if (!props.currentUser) return props.users;
  const selfId = props.currentUser.id;
  return [...props.users].sort((a, b) => {
    if (a.id === selfId) return -1;
    if (b.id === selfId) return 1;
    return a.joinedAt - b.joinedAt;
  });
});

const statusMeta = computed(() => {
  switch (props.status) {
    case 'connected':
      return {
        label: '已连接',
        dotClass: 'bg-[#34C759] dark:bg-[#30D158]',
        textClass: 'text-[#248A3D] dark:text-[#30D158]',
      };
    case 'reconnecting':
    case 'connecting':
      return {
        label: '重连中',
        dotClass: 'bg-[#FF9500] dark:bg-[#FF9F0A]',
        textClass: 'text-[#C96B00] dark:text-[#FF9F0A]',
      };
    default:
      return {
        label: '已断开',
        dotClass: 'bg-[#FF3B30] dark:bg-[#FF453A]',
        textClass: 'text-[#D70015] dark:text-[#FF453A]',
      };
  }
});
</script>

<template>
  <div class="flex flex-col h-full bg-[#F5F5F7] dark:bg-[#141416]">
    <!-- Sidebar Header -->
    <div
      class="shrink-0 border-b border-black/[0.06] dark:border-white/[0.08]"
      :class="showCloseButton ? 'pt-[env(safe-area-inset-top)]' : ''"
    >
      <div
        class="h-14 pl-5 pr-[max(1.25rem,env(safe-area-inset-right))] flex items-center justify-between"
      >
        <div class="flex items-center gap-2 min-w-0">
          <h2 class="text-[13px] font-semibold tracking-tight text-[#1C1C1E] dark:text-[#F5F5F7] whitespace-nowrap">
            在线成员
          </h2>
          <span class="text-black/20 dark:text-white/20" aria-hidden="true">·</span>
          <span class="text-xs font-mono-tabular text-[#636366] dark:text-[#98989F]">
            {{ users.length }}
          </span>
        </div>

        <button
          v-if="showCloseButton"
          type="button"
          @click="emit('close')"
          aria-label="关闭在线成员列表"
          class="h-8 w-8 inline-flex items-center justify-center rounded-full bg-black/[0.05] text-[#636366] hover:bg-black/[0.09] hover:text-[#1C1C1E] dark:bg-white/[0.09] dark:text-[#98989F] dark:hover:bg-white/[0.15] dark:hover:text-[#F5F5F7] transition-colors cursor-pointer"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- Member List -->
    <div class="flex-1 overflow-y-auto chat-scroll px-3 py-3 space-y-1">
      <div
        v-if="sortedUsers.length === 0"
        class="py-12 px-3 text-center text-xs text-[#8E8E93]"
      >
        暂无其他在线成员
      </div>

      <div
        v-for="user in sortedUsers"
        :key="user.id"
        class="flex items-center gap-3 px-3 py-2.5 rounded-[14px] transition-colors duration-150"
        :class="
          currentUser && user.id === currentUser.id
            ? 'bg-white shadow-[0_1px_3px_rgba(0,0,0,0.05)] ring-1 ring-black/[0.04] dark:bg-[#222225] dark:shadow-none dark:ring-white/[0.06]'
            : 'hover:bg-black/[0.04] dark:hover:bg-white/[0.05]'
        "
      >
        <div class="relative shrink-0">
          <div
            class="h-9 w-9 rounded-full flex items-center justify-center text-[13px] font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.22)]"
            :class="getAvatarBgClass(user.avatarColor)"
          >
            {{ getAvatarInitials(user.nickname) }}
          </div>
          <!-- Real live WebSocket presence indicator -->
          <span
            class="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-[#34C759] dark:bg-[#30D158] ring-2 ring-[#F5F5F7] dark:ring-[#141416]"
            aria-hidden="true"
          />
        </div>

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-1.5 min-w-0">
            <span
              class="text-[14px] font-medium text-[#1C1C1E] dark:text-[#F5F5F7] truncate"
              :title="user.nickname"
            >
              {{ user.nickname }}
            </span>
            <span
              v-if="currentUser && user.id === currentUser.id"
              class="text-xs text-[#007AFF] dark:text-[#0A84FF] shrink-0 font-medium"
            >
              · 我
            </span>
          </div>
          <p class="text-[11px] font-mono-tabular text-[#8E8E93] dark:text-[#8E8E93] mt-0.5">
            {{ formatMessageTime(user.joinedAt) }} 加入
          </p>
        </div>
      </div>
    </div>

    <!-- Self Status Footer -->
    <div
      v-if="currentUser"
      class="shrink-0 pl-5 pr-[max(1.25rem,env(safe-area-inset-right))] pt-3.5 pb-[max(0.875rem,env(safe-area-inset-bottom))] border-t border-black/[0.06] dark:border-white/[0.08] bg-white/60 dark:bg-[#1C1C1E]/60"
    >
      <div class="flex items-center justify-between gap-2 text-xs">
        <span class="text-[#636366] dark:text-[#98989F] truncate">
          {{ currentUser.nickname }}
        </span>
        <span class="inline-flex items-center gap-1.5 shrink-0 font-medium" :class="statusMeta.textClass">
          <span class="h-1.5 w-1.5 rounded-full shrink-0" :class="statusMeta.dotClass" aria-hidden="true" />
          <span>{{ statusMeta.label }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
