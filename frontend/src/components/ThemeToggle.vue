<script setup lang="ts">
import {Moon, Sun} from 'lucide-vue-next';
import {useTheme} from '@/composables/useTheme';

const {isDark, toggleTheme} = useTheme();
</script>

<template>
  <button
    type="button"
    @click="toggleTheme"
    :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
    :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
    class="inline-flex items-center justify-center gap-1.5 h-9 px-3.5 rounded-full text-[13px] font-medium whitespace-nowrap shrink-0 bg-black/[0.05] text-[#1C1C1E] hover:bg-black/[0.09] active:scale-[0.96] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-white/[0.15] transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] dark:focus-visible:ring-[#0A84FF] cursor-pointer"
  >
    <Sun v-if="isDark" class="w-3.5 h-3.5 text-[#FFD60A] shrink-0" />
    <Moon v-else class="w-3.5 h-3.5 text-[#3A3A3C] shrink-0" />
    <span>{{ isDark ? '浅色' : '深色' }}</span>
  </button>
</template>
