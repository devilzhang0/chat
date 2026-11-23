<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';

const emit = defineEmits<{
  (e: 'select', emoji: string): void;
  (e: 'close'): void;
}>();

interface EmojiCategory {
  id: string;
  label: string;
  emojis: string[];
}

const CATEGORIES: EmojiCategory[] = [
  {
    id: 'common',
    label: '常用',
    emojis: [
      '😀', '😄', '😁', '😂', '🤣', '😊', '🙂', '😉',
      '😍', '🥰', '😎', '🤔', '😅', '😭', '😮', '🥳',
      '👍', '👏', '🙏', '🤝', '👌', '✌️', '❤️', '🔥',
    ],
  },
  {
    id: 'faces',
    label: '表情',
    emojis: [
      '😃', '😇', '🤩', '😋', '😜', '🤪', '🤨', '🧐',
      '🤓', '😏', '😌', '😴', '🤯', '😳', '🥺', '😤',
      '😬', '🤫', '🤭', '🫣', '🫡', '🫠', '😵‍💫', '🤗',
    ],
  },
  {
    id: 'gestures',
    label: '手势',
    emojis: [
      '👋', '🤚', '✋', '🖖', '🫶', '🙌', '👐', '🤲',
      '✊', '👊', '🤛', '🤜', '🤞', '🤟', '🤘', '🤙',
      '👈', '👉', '👆', '👇', '☝️', '💪', '✍️', '🎉',
    ],
  },
  {
    id: 'daily',
    label: '日常',
    emojis: [
      '☕', '🍵', '🧋', '🍰', '🍜', '🍕', '🌟', '✨',
      '💡', '📌', '💻', '📱', '🎵', '🎧', '📚', '🗓️',
      '🚀', '⚡', '🌈', '☀️', '🌙', '🍀', '🎯', '✅',
    ],
  },
];

const activeTab = ref<string>('common');
const pickerRef = ref<HTMLElement | null>(null);

const currentEmojis = () => {
  return CATEGORIES.find((c) => c.id === activeTab.value)?.emojis ?? CATEGORIES[0].emojis;
};

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    emit('close');
  }
}

function handleClickOutside(e: MouseEvent): void {
  const target = e.target as HTMLElement | null;
  if (!pickerRef.value || !target) return;
  if (!pickerRef.value.contains(target) && !target.closest('[data-emoji-trigger]')) {
    emit('close');
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown);
  document.addEventListener('mousedown', handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown);
  document.removeEventListener('mousedown', handleClickOutside);
});
</script>

<template>
  <div
    ref="pickerRef"
    role="dialog"
    aria-label="选择常用表情"
    class="w-[312px] rounded-[22px] ios-glass-popover ring-1 ring-black/[0.07] dark:ring-white/[0.11] shadow-[0_20px_50px_-12px_rgba(0,0,0,0.18)] dark:shadow-[0_24px_60px_-12px_rgba(0,0,0,0.65)] overflow-hidden select-none p-2.5"
  >
    <!-- iOS Segmented Control -->
    <div class="flex items-center gap-0.5 p-0.5 rounded-[11px] bg-black/[0.06] dark:bg-white/[0.09] mb-2.5">
      <button
        v-for="cat in CATEGORIES"
        :key="cat.id"
        type="button"
        @click="activeTab = cat.id"
        class="flex-1 py-1.5 text-xs font-medium rounded-[9px] transition-all duration-150 whitespace-nowrap cursor-pointer"
        :class="
          activeTab === cat.id
            ? 'bg-white text-[#1C1C1E] shadow-[0_1px_3px_rgba(0,0,0,0.1)] dark:bg-[#3A3A3C] dark:text-[#F5F5F7] dark:shadow-[0_1px_4px_rgba(0,0,0,0.4)]'
            : 'text-[#636366] hover:text-[#1C1C1E] dark:text-[#98989F] dark:hover:text-[#F5F5F7]'
        "
      >
        {{ cat.label }}
      </button>
    </div>

    <div class="grid grid-cols-8 gap-0.5">
      <button
        v-for="emoji in currentEmojis()"
        :key="emoji"
        type="button"
        @click="emit('select', emoji)"
        class="h-9 w-9 flex items-center justify-center rounded-[10px] text-[19px] hover:bg-black/[0.06] active:scale-90 dark:hover:bg-white/[0.1] transition-transform duration-100 cursor-pointer"
      >
        {{ emoji }}
      </button>
    </div>
  </div>
</template>
