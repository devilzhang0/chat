<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue';
import {
  AlertCircle,
  ArrowDown,
  ArrowUp,
  Check,
  Copy,
  Link2,
  Loader2,
  Lock,
  LogOut,
  MessageSquare,
  RefreshCw,
  Smile,
  Users,
  Volume2,
  VolumeX,
} from 'lucide-vue-next';
import EmojiPicker from '@/components/EmojiPicker.vue';
import MessageContent from '@/components/MessageContent.vue';
import OnlineUserList from '@/components/OnlineUserList.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';
import {useChatSocket} from '@/composables/useChatSocket';
import {useMobileViewport} from '@/composables/useMobileViewport';
import {useNotification} from '@/composables/useNotification';
import {getAvatarBgClass, getAvatarInitials, type ChatMessage} from '@/types/chat';
import {formatDateDivider, formatDateKey, formatMessageTime} from '@/utils/formatTime';

const {
  currentUser,
  currentRoom,
  messages,
  lastIncomingMessage,
  onlineUsers,
  status,
  isLoadingHistory,
  hasMoreHistory,
  isLoadingMoreHistory,
  errorMessage,
  sendMessage,
  retryMessage,
  loadOlderHistory,
  manualReconnect,
  leaveRoom,
  clearError,
} = useChatSocket();

const draft = ref('');
const showEmojiPicker = ref(false);
const showMobileUsers = ref(false);
const isComposing = ref(false);
const isAtBottom = ref(true);
const unreadBelowCount = ref(0);
const isInitialScrollReady = ref(false);
const copiedShare = ref(false);
const copiedCode = ref(false);
let copiedShareTimer: number | null = null;
let copiedCodeTimer: number | null = null;

const scrollContainerRef = ref<HTMLElement | null>(null);
const textareaRef = ref<HTMLTextAreaElement | null>(null);

const roomNameRef = computed(() => currentRoom.value?.name);
const {soundEnabled, toggleSound, notifyIncomingMessage} = useNotification(roomNameRef);

watch(lastIncomingMessage, (msg) => {
  if (!msg || msg.type !== 'text') return;
  if (currentUser.value && msg.senderId === currentUser.value.id) return;
  notifyIncomingMessage();
});

const {viewportHeight, viewportOffsetTop, isKeyboardOpen, syncViewport} = useMobileViewport(() => {
  if (isAtBottom.value) {
    nextTick(() => scrollToBottom(false));
  }
});

const chatShellStyle = computed<Record<string, string>>(() => {
  if (viewportHeight.value === null) return {};
  return {
    height: `${viewportHeight.value}px`,
    top: `${viewportOffsetTop.value}px`,
  };
});

function isCoarsePointerDevice(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches;
}

function handleTextareaFocus(): void {
  if (showEmojiPicker.value) {
    showEmojiPicker.value = false;
  }
  syncViewport();
  if (isAtBottom.value) {
    window.setTimeout(() => {
      if (isAtBottom.value) {
        scrollToBottom(false);
      }
    }, 120);
    window.setTimeout(() => {
      if (isAtBottom.value) {
        scrollToBottom(false);
      }
    }, 320);
  }
}

function handleToggleEmoji(): void {
  const nextOpen = !showEmojiPicker.value;
  if (nextOpen && (isKeyboardOpen.value || isCoarsePointerDevice())) {
    textareaRef.value?.blur();
  }
  showEmojiPicker.value = nextOpen;
}

let chatTouchStart: {x: number; y: number} | null = null;

function isInteractiveTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    Boolean(target.closest('button, a, input, textarea, [role="button"]'))
  );
}

function dismissInputOverlay(target: EventTarget | null): void {
  if (isInteractiveTarget(target)) return;
  const selectedText = window.getSelection?.()?.toString();
  if (selectedText && selectedText.length > 0) return;

  if (document.activeElement === textareaRef.value) {
    textareaRef.value?.blur();
  }
  if (showEmojiPicker.value) {
    showEmojiPicker.value = false;
  }
}

function handleChatTouchStart(e: TouchEvent): void {
  const touch = e.touches[0];
  if (!touch) return;
  chatTouchStart = {x: touch.clientX, y: touch.clientY};
}

function handleChatTouchEnd(e: TouchEvent): void {
  const touch = e.changedTouches[0];
  if (!touch || !chatTouchStart) {
    chatTouchStart = null;
    return;
  }
  const dx = Math.abs(touch.clientX - chatTouchStart.x);
  const dy = Math.abs(touch.clientY - chatTouchStart.y);
  chatTouchStart = null;
  if (dx < 10 && dy < 10) {
    dismissInputOverlay(e.target);
  }
}

function handleChatClick(e: MouseEvent): void {
  dismissInputOverlay(e.target);
}

function handleOpenMobileUsers(): void {
  textareaRef.value?.blur();
  showEmojiPicker.value = false;
  showMobileUsers.value = true;
}

const STARTER_PROMPTS = ['大家好 👋', '刚进房间，聊点什么？', '今天过得怎么样？'];

const shareUrl = computed(() => {
  if (!currentRoom.value) return '';
  return `${window.location.origin}/?room=${encodeURIComponent(currentRoom.value.code)}`;
});

async function copyToClipboard(text: string): Promise<boolean> {
  if (!text) return false;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // fallback below
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

async function handleCopyShareLink(): Promise<void> {
  if (!shareUrl.value) return;
  const ok = await copyToClipboard(shareUrl.value);
  if (ok) {
    copiedShare.value = true;
    if (copiedShareTimer !== null) window.clearTimeout(copiedShareTimer);
    copiedShareTimer = window.setTimeout(() => {
      copiedShare.value = false;
      copiedShareTimer = null;
    }, 2200);
  }
}

async function handleCopyRoomCode(): Promise<void> {
  if (!currentRoom.value) return;
  const ok = await copyToClipboard(currentRoom.value.code);
  if (ok) {
    copiedCode.value = true;
    if (copiedCodeTimer !== null) window.clearTimeout(copiedCodeTimer);
    copiedCodeTimer = window.setTimeout(() => {
      copiedCode.value = false;
      copiedCodeTimer = null;
    }, 1800);
  }
}

const MAX_LENGTH = 500;
const draftCharCount = computed(() => Array.from(draft.value).length);
const canSend = computed(
  () =>
    draft.value.trim().length > 0 &&
    draftCharCount.value <= MAX_LENGTH &&
    status.value === 'connected'
);

interface RenderItem {
  kind: 'date' | 'system' | 'message';
  key: string;
  dateLabel?: string;
  message?: ChatMessage;
  isSelf?: boolean;
  isCompact?: boolean;
}

const renderItems = computed<RenderItem[]>(() => {
  const items: RenderItem[] = [];
  let lastDateKey = '';
  let prevTextMsg: ChatMessage | null = null;

  for (const msg of messages.value) {
    const dateKey = formatDateKey(msg.timestamp);
    if (dateKey !== lastDateKey) {
      items.push({
        kind: 'date',
        key: `date-${dateKey}`,
        dateLabel: formatDateDivider(msg.timestamp),
      });
      lastDateKey = dateKey;
      prevTextMsg = null;
    }

    if (msg.type === 'system') {
      items.push({
        kind: 'system',
        key: msg.id,
        message: msg,
      });
      prevTextMsg = null;
      continue;
    }

    const isSelf = Boolean(currentUser.value && msg.senderId === currentUser.value.id);
    const isCompact = Boolean(
      prevTextMsg &&
        prevTextMsg.senderId === msg.senderId &&
        msg.timestamp - prevTextMsg.timestamp < 3 * 60 * 1000
    );

    items.push({
      kind: 'message',
      key: msg.clientTempId || msg.id,
      message: msg,
      isSelf,
      isCompact,
    });
    prevTextMsg = msg;
  }

  return items;
});

function scrollToBottom(smooth = false): void {
  const el = scrollContainerRef.value;
  if (!el) return;
  el.scrollTo({
    top: el.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto',
  });
  isAtBottom.value = true;
  unreadBelowCount.value = 0;
}

async function handleLoadOlderHistory(): Promise<void> {
  if (!hasMoreHistory.value || isLoadingMoreHistory.value || status.value !== 'connected') {
    return;
  }
  const el = scrollContainerRef.value;
  const prevScrollHeight = el ? el.scrollHeight : 0;
  const prevScrollTop = el ? el.scrollTop : 0;

  const added = await loadOlderHistory();
  if (added > 0 && el) {
    await nextTick();
    const heightDelta = el.scrollHeight - prevScrollHeight;
    el.scrollTop = prevScrollTop + heightDelta;
  }
}

function handleScroll(): void {
  const el = scrollContainerRef.value;
  if (!el) return;
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
  const nearBottom = distanceFromBottom < 80;
  isAtBottom.value = nearBottom;
  if (nearBottom) {
    unreadBelowCount.value = 0;
  }
  if (
    isInitialScrollReady.value &&
    el.scrollTop <= 80 &&
    hasMoreHistory.value &&
    !isLoadingMoreHistory.value &&
    status.value === 'connected'
  ) {
    void handleLoadOlderHistory();
  }
}

function resizeTextarea(): void {
  const el = textareaRef.value;
  if (!el) return;
  el.style.height = 'auto';
  const maxHeight = isKeyboardOpen.value ? 96 : 136;
  const nextHeight = Math.min(el.scrollHeight, maxHeight);
  el.style.height = `${nextHeight}px`;
}

function handleSend(): void {
  if (!canSend.value) return;
  const wasFocused = document.activeElement === textareaRef.value;
  const sent = sendMessage(draft.value);
  if (sent) {
    draft.value = '';
    showEmojiPicker.value = false;
    nextTick(() => {
      resizeTextarea();
      scrollToBottom(true);
      if (!isCoarsePointerDevice() || wasFocused) {
        textareaRef.value?.focus();
      }
    });
  }
}

function handleStarterSend(text: string): void {
  if (status.value !== 'connected') return;
  sendMessage(text);
  nextTick(() => {
    scrollToBottom(true);
    if (!isCoarsePointerDevice()) {
      textareaRef.value?.focus();
    }
  });
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !isComposing.value) {
    e.preventDefault();
    handleSend();
  }
}

function insertEmoji(emoji: string): void {
  const el = textareaRef.value;
  if (!el) {
    draft.value += emoji;
    return;
  }
  const start = el.selectionStart ?? draft.value.length;
  const end = el.selectionEnd ?? draft.value.length;
  draft.value = draft.value.slice(0, start) + emoji + draft.value.slice(end);
  nextTick(() => {
    const nextPos = start + emoji.length;
    if (!isCoarsePointerDevice()) {
      el.focus();
      el.setSelectionRange(nextPos, nextPos);
    }
    resizeTextarea();
  });
}

function handleRetry(msg: ChatMessage): void {
  const key = msg.clientTempId || msg.id;
  retryMessage(key);
}

watch(
  () => {
    const len = messages.value.length;
    const last = len > 0 ? messages.value[len - 1] : null;
    return {
      len,
      lastKey: last ? last.clientTempId || last.id : null,
    };
  },
  (next, prev) => {
    if (next.len === 0) return;
    // 顶部向上分页插入历史消息或队尾乐观消息回执替换时，队尾 key 不变，不误触滚底和未读计数
    if (prev && prev.len > 0 && next.lastKey === prev.lastKey) {
      return;
    }

    const oldLen = prev ? prev.len : 0;
    const latest = messages.value[next.len - 1];
    const isSelfLatest = Boolean(
      latest && currentUser.value && latest.senderId === currentUser.value.id && latest.type === 'text'
    );

    nextTick(() => {
      if (oldLen === 0 || isAtBottom.value || isSelfLatest) {
        scrollToBottom(oldLen > 0);
      } else if (next.len > oldLen) {
        unreadBelowCount.value += next.len - oldLen;
      }
    });
  }
);

onMounted(() => {
  nextTick(() => {
    scrollToBottom(false);
    isInitialScrollReady.value = true;
    if (!isCoarsePointerDevice()) {
      textareaRef.value?.focus();
    }
  });
});
</script>

<template>
  <div
    :style="chatShellStyle"
    class="fixed inset-x-0 top-0 h-dvh flex flex-col overflow-hidden bg-white text-[#1C1C1E] dark:bg-black dark:text-[#F5F5F7] transition-colors duration-200"
  >
    <!-- Frosted iOS Top Header -->
    <header
      @click="handleChatClick"
      class="shrink-0 px-3.5 sm:px-6 flex items-center justify-between gap-2.5 sm:gap-3 ios-glass-bar border-b border-black/[0.06] dark:border-white/[0.08] z-20"
      :class="isKeyboardOpen ? 'h-12 sm:h-14' : 'h-14'"
    >
      <div class="flex items-center gap-2.5 min-w-0">
        <span
          class="h-8 w-8 rounded-[10px] text-white inline-flex items-center justify-center shrink-0"
          :class="
            currentRoom?.isPrivate
              ? 'bg-gradient-to-b from-[#FFAA33] to-[#FF9500] dark:from-[#FFB340] dark:to-[#FF9F0A] shadow-[0_2px_8px_rgba(255,149,0,0.28)]'
              : 'bg-gradient-to-b from-[#3395FF] to-[#007AFF] dark:from-[#2997FF] dark:to-[#0A84FF] shadow-[0_2px_8px_rgba(0,122,255,0.28)]'
          "
          aria-hidden="true"
        >
          <Lock v-if="currentRoom?.isPrivate" class="w-4 h-4" />
          <MessageSquare v-else class="w-4 h-4 fill-white/15" />
        </span>

        <div class="flex items-center gap-2 min-w-0">
          <h1
            class="text-[15px] font-semibold tracking-[-0.015em] text-[#1C1C1E] dark:text-[#F5F5F7] truncate max-w-[140px] sm:max-w-[240px]"
            :title="currentRoom?.name"
          >
            {{ currentRoom?.name }}
          </h1>

          <button
            v-if="currentRoom"
            type="button"
            @click="handleCopyRoomCode"
            title="点击复制房间号"
            class="inline-flex items-center gap-1 h-6 px-2 rounded-full text-[12px] font-mono-tabular bg-black/[0.05] text-[#636366] hover:bg-black/[0.09] hover:text-[#1C1C1E] dark:bg-white/[0.09] dark:text-[#98989F] dark:hover:bg-white/[0.15] dark:hover:text-[#F5F5F7] transition-colors shrink-0 cursor-pointer"
          >
            <span>#{{ currentRoom.code }}</span>
            <Check v-if="copiedCode" class="w-3 h-3 text-[#34C759] dark:text-[#30D158]" />
            <Copy v-else class="w-3 h-3 opacity-70" />
          </button>

          <span class="hidden md:inline text-[13px] text-[#636366] dark:text-[#98989F] whitespace-nowrap shrink-0">
            <span class="font-mono-tabular font-semibold text-[#1C1C1E] dark:text-[#F5F5F7]">{{ onlineUsers.length }}</span>
            人在线
          </span>
        </div>
      </div>

      <div class="flex items-center gap-2 shrink-0">
        <!-- Share Room Link button -->
        <button
          type="button"
          @click="handleCopyShareLink"
          :title="copiedShare ? '已复制分享链接' : '复制房间分享链接'"
          class="inline-flex items-center justify-center gap-1.5 h-9 px-3 sm:px-3.5 rounded-full text-[13px] font-medium whitespace-nowrap shrink-0 active:scale-[0.96] transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] cursor-pointer"
          :class="
            copiedShare
              ? 'bg-[#34C759]/15 text-[#248A3D] dark:bg-[#30D158]/20 dark:text-[#30D158]'
              : 'bg-[#007AFF]/10 text-[#007AFF] hover:bg-[#007AFF]/16 dark:bg-[#0A84FF]/18 dark:text-[#409CFF] dark:hover:bg-[#0A84FF]/26'
          "
        >
          <Check v-if="copiedShare" class="w-3.5 h-3.5 shrink-0" />
          <Link2 v-else class="w-3.5 h-3.5 shrink-0" />
          <span class="hidden sm:inline">{{ copiedShare ? '已复制链接' : '分享链接' }}</span>
        </button>

        <!-- Mobile online members trigger -->
        <button
          type="button"
          @click="handleOpenMobileUsers"
          class="md:hidden inline-flex items-center justify-center gap-1.5 h-9 px-3 rounded-full text-[13px] font-medium whitespace-nowrap shrink-0 bg-black/[0.05] text-[#1C1C1E] hover:bg-black/[0.09] active:scale-[0.96] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-white/[0.15] transition-all cursor-pointer"
          aria-label="查看在线成员"
        >
          <Users class="w-3.5 h-3.5 shrink-0" />
          <span class="font-mono-tabular">{{ onlineUsers.length }}</span>
        </button>

        <!-- Message sound toggle -->
        <button
          type="button"
          @click="toggleSound"
          :aria-pressed="soundEnabled"
          :aria-label="soundEnabled ? '关闭消息提示音' : '开启消息提示音'"
          :title="soundEnabled ? '已开启消息提示音（点击静音）' : '已静音（点击开启提示音）'"
          class="inline-flex items-center justify-center h-9 w-9 rounded-full shrink-0 active:scale-[0.96] transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] dark:focus-visible:ring-[#0A84FF] cursor-pointer"
          :class="
            soundEnabled
              ? 'bg-black/[0.05] text-[#1C1C1E] hover:bg-black/[0.09] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-white/[0.15]'
              : 'bg-black/[0.04] text-[#8E8E93] hover:bg-black/[0.08] hover:text-[#636366] dark:bg-white/[0.06] dark:text-[#636366] dark:hover:bg-white/[0.12] dark:hover:text-[#98989F]'
          "
        >
          <Volume2 v-if="soundEnabled" class="w-4 h-4 shrink-0" />
          <VolumeX v-else class="w-4 h-4 shrink-0" />
        </button>

        <ThemeToggle />

        <button
          type="button"
          @click="leaveRoom"
          class="inline-flex items-center justify-center gap-1.5 h-9 px-3 sm:px-3.5 rounded-full text-[13px] font-medium whitespace-nowrap shrink-0 bg-black/[0.05] text-[#1C1C1E] hover:bg-[#FF3B30]/12 hover:text-[#D70015] active:scale-[0.96] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-[#FF453A]/18 dark:hover:text-[#FF453A] transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] cursor-pointer"
        >
          <LogOut class="w-3.5 h-3.5 shrink-0" />
          <span class="hidden sm:inline">离开房间</span>
        </button>
      </div>
    </header>

    <!-- Live connection / error status banner -->
    <div
      v-if="status === 'reconnecting' || status === 'disconnected'"
      role="status"
      class="shrink-0 px-4 sm:px-6 py-2.5 border-b text-[13px] flex items-center justify-between gap-3"
      :class="
        status === 'reconnecting'
          ? 'bg-[#FF9500]/10 border-[#FF9500]/20 text-[#B25000] dark:bg-[#FF9F0A]/12 dark:border-[#FF9F0A]/20 dark:text-[#FF9F0A]'
          : 'bg-[#FF3B30]/10 border-[#FF3B30]/20 text-[#D70015] dark:bg-[#FF453A]/12 dark:border-[#FF453A]/20 dark:text-[#FF453A]'
      "
    >
      <div class="flex items-center gap-2 min-w-0">
        <AlertCircle class="w-4 h-4 shrink-0" />
        <span class="truncate">
          {{ status === 'reconnecting' ? '实时连接已中断，正在自动重连...' : '与聊天服务器断开连接，暂无法收发消息。' }}
        </span>
      </div>
      <button
        type="button"
        @click="manualReconnect"
        class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium whitespace-nowrap shrink-0 bg-white/90 hover:bg-white text-[#1C1C1E] shadow-xs dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C] dark:text-[#F5F5F7] transition-colors cursor-pointer"
      >
        <RefreshCw class="w-3 h-3" />
        <span>立即重连</span>
      </button>
    </div>

    <div
      v-else-if="errorMessage"
      role="alert"
      class="shrink-0 px-4 sm:px-6 py-2.5 border-b border-[#FF3B30]/20 bg-[#FF3B30]/10 text-[#D70015] dark:border-[#FF453A]/20 dark:bg-[#FF453A]/12 dark:text-[#FF453A] text-[13px] flex items-center justify-between gap-3"
    >
      <span>{{ errorMessage }}</span>
      <button
        type="button"
        @click="clearError"
        class="text-xs font-medium underline underline-offset-2 shrink-0 cursor-pointer"
      >
        关闭
      </button>
    </div>

    <!-- Main Workspace: Chat Stream + Online Sidebar -->
    <div class="flex-1 flex min-h-0 overflow-hidden">
      <!-- Chat Stream Column -->
      <main class="flex-1 flex flex-col min-w-0 min-h-0 bg-white dark:bg-black">
        <div class="flex-1 relative min-h-0 flex flex-col">
          <!-- Scrollable Messages -->
          <div
            ref="scrollContainerRef"
            @scroll="handleScroll"
            @touchstart.passive="handleChatTouchStart"
            @touchend.passive="handleChatTouchEnd"
            @click="handleChatClick"
            class="flex-1 overflow-y-auto chat-scroll px-3.5 sm:px-7"
            :class="isKeyboardOpen ? 'py-3' : 'py-5 sm:py-6'"
          >
          <div class="max-w-3xl mx-auto w-full flex flex-col">
            <!-- Loading skeleton -->
            <div v-if="isLoadingHistory" class="space-y-5 py-4" aria-label="正在加载历史记录">
              <div v-for="n in 4" :key="n" class="flex items-start gap-3 animate-pulse">
                <div class="h-8 w-8 rounded-full bg-black/[0.06] dark:bg-white/[0.08] shrink-0" />
                <div class="space-y-2 flex-1 max-w-md">
                  <div class="h-3 w-20 rounded-full bg-black/[0.06] dark:bg-white/[0.08]" />
                  <div class="h-11 w-full rounded-[20px] bg-black/[0.05] dark:bg-white/[0.07]" />
                </div>
              </div>
            </div>

            <!-- Empty state -->
            <div
              v-else-if="renderItems.length === 0"
              class="py-24 text-center flex flex-col items-center justify-center"
            >
              <div
                class="h-14 w-14 rounded-[18px] bg-[#F2F2F7] text-[#8E8E93] dark:bg-[#1C1C1E] dark:text-[#8E8E93] flex items-center justify-center mb-4"
              >
                <MessageSquare class="w-6 h-6" />
              </div>
              <p class="text-[16px] font-semibold tracking-[-0.015em] text-[#1C1C1E] dark:text-[#F5F5F7]">
                「{{ currentRoom?.name }}」已就绪
              </p>
              <p class="mt-1 text-[13px] text-[#8E8E93]">
                房间号 <span class="font-mono-tabular">#{{ currentRoom?.code }}</span> · 还没有聊天记录，发一句问候或邀请好友加入吧
              </p>
              <div class="mt-5 flex flex-wrap items-center justify-center gap-2">
                <button
                  type="button"
                  @click="handleCopyShareLink"
                  class="inline-flex items-center gap-1.5 px-4 py-2 rounded-full text-[13px] font-medium transition-all active:scale-[0.97] cursor-pointer"
                  :class="
                    copiedShare
                      ? 'bg-[#34C759]/15 text-[#248A3D] dark:bg-[#30D158]/20 dark:text-[#30D158]'
                      : 'bg-[#007AFF]/10 text-[#007AFF] hover:bg-[#007AFF]/16 dark:bg-[#0A84FF]/18 dark:text-[#409CFF] dark:hover:bg-[#0A84FF]/26'
                  "
                >
                  <Check v-if="copiedShare" class="w-3.5 h-3.5" />
                  <Link2 v-else class="w-3.5 h-3.5" />
                  <span>{{ copiedShare ? '分享链接已复制' : '复制房间分享链接' }}</span>
                </button>
              </div>
              <div class="mt-3 flex flex-wrap items-center justify-center gap-2">
                <button
                  v-for="starter in STARTER_PROMPTS"
                  :key="starter"
                  type="button"
                  @click="handleStarterSend(starter)"
                  class="px-4 py-2 rounded-full text-[13px] font-medium bg-[#F2F2F7] text-[#1C1C1E] hover:bg-[#E5E5EA] active:scale-[0.97] dark:bg-[#1C1C1E] dark:text-[#F5F5F7] dark:hover:bg-[#2C2C2E] transition-all cursor-pointer"
                >
                  {{ starter }}
                </button>
              </div>
            </div>

            <!-- Populated timeline -->
            <template v-else>
              <!-- History pagination header -->
              <div class="mb-4 flex items-center justify-center select-none">
                <div
                  v-if="isLoadingMoreHistory"
                  class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[12px] text-[#636366] dark:text-[#98989F] bg-black/[0.04] dark:bg-white/[0.07]"
                >
                  <Loader2 class="w-3.5 h-3.5 animate-spin text-[#007AFF] dark:text-[#0A84FF]" />
                  <span>正在加载更早的消息...</span>
                </div>
                <button
                  v-else-if="hasMoreHistory"
                  type="button"
                  @click="handleLoadOlderHistory"
                  :disabled="status !== 'connected'"
                  class="px-3.5 py-1 rounded-full text-[12px] font-medium text-[#007AFF] hover:bg-[#007AFF]/10 dark:text-[#409CFF] dark:hover:bg-[#0A84FF]/15 transition-colors disabled:opacity-45 cursor-pointer"
                >
                  加载更早的消息
                </button>
                <span
                  v-else
                  class="text-[11px] text-[#8E8E93]/80"
                >
                  已显示全部历史消息
                </span>
              </div>

              <template v-for="item in renderItems" :key="item.key">
                <!-- iOS Date Divider -->
                <div
                  v-if="item.kind === 'date'"
                  class="my-6 flex items-center justify-center select-none"
                >
                  <span class="text-[12px] font-medium text-[#8E8E93] tracking-tight">
                    {{ item.dateLabel }}
                  </span>
                </div>

                <!-- System join / leave notice -->
                <div
                  v-else-if="item.kind === 'system' && item.message"
                  class="my-3 flex items-center justify-center text-center"
                >
                  <div class="inline-flex items-center gap-1.5 text-[12px] text-[#8E8E93]">
                    <span>{{ item.message.content }}</span>
                    <span aria-hidden="true">·</span>
                    <span class="font-mono-tabular text-[11px]">
                      {{ formatMessageTime(item.message.timestamp) }}
                    </span>
                  </div>
                </div>

                <!-- Regular text message -->
                <div
                  v-else-if="item.kind === 'message' && item.message"
                  class="flex"
                  :class="[
                    item.isSelf ? 'justify-end' : 'justify-start',
                    item.isCompact ? 'mt-1' : 'mt-4',
                  ]"
                >
                  <!-- Other user message (iMessage Incoming) -->
                  <div
                    v-if="!item.isSelf"
                    class="flex items-end gap-2.5 max-w-[82%] sm:max-w-[70%]"
                  >
                    <div class="w-8 shrink-0">
                      <div
                        v-if="!item.isCompact"
                        class="h-8 w-8 rounded-full flex items-center justify-center text-xs font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.22)]"
                        :class="getAvatarBgClass(item.message.avatarColor)"
                      >
                        {{ getAvatarInitials(item.message.senderName) }}
                      </div>
                    </div>

                    <div class="min-w-0">
                      <div
                        v-if="!item.isCompact"
                        class="flex items-baseline gap-2 mb-1.5 pl-1"
                      >
                        <span class="text-[12px] font-medium text-[#636366] dark:text-[#98989F] truncate max-w-[180px]">
                          {{ item.message.senderName }}
                        </span>
                        <span class="text-[11px] font-mono-tabular text-[#8E8E93] shrink-0">
                          {{ formatMessageTime(item.message.timestamp) }}
                        </span>
                      </div>
                      <div
                        class="px-4 py-2.5 rounded-[20px] rounded-bl-[6px] bg-[#E9E9EB] text-[#1C1C1E] dark:bg-[#262629] dark:text-[#F5F5F7] text-[15px] leading-[1.48] whitespace-pre-wrap break-words"
                      >
                        <MessageContent :content="item.message.content" :is-self="false" />
                      </div>
                    </div>
                  </div>

                  <!-- Self message (iMessage Outgoing) -->
                  <div
                    v-else
                    class="flex flex-col items-end max-w-[82%] sm:max-w-[70%]"
                  >
                    <div
                      v-if="!item.isCompact"
                      class="flex items-baseline gap-2 mb-1.5 pr-1"
                    >
                      <span class="text-[11px] font-mono-tabular text-[#8E8E93] shrink-0">
                        {{ formatMessageTime(item.message.timestamp) }}
                      </span>
                      <span class="text-[12px] font-medium text-[#636366] dark:text-[#98989F]">
                        我
                      </span>
                    </div>
                    <div class="flex items-center justify-end gap-2 max-w-full">
                      <!-- Sending spinner -->
                      <span
                        v-if="item.message.sendStatus === 'sending'"
                        class="inline-flex items-center text-[#8E8E93] shrink-0"
                        title="发送中..."
                        aria-label="发送中"
                      >
                        <Loader2 class="w-3.5 h-3.5 animate-spin" />
                      </span>

                      <!-- Failed one-click retry button -->
                      <button
                        v-else-if="item.message.sendStatus === 'failed'"
                        type="button"
                        @click="handleRetry(item.message)"
                        :title="item.message.failReason || '发送失败，点击重发'"
                        aria-label="重新发送消息"
                        class="inline-flex items-center gap-1 h-7 px-2.5 rounded-full text-[12px] font-medium shrink-0 bg-[#FF3B30]/12 text-[#D70015] hover:bg-[#FF3B30]/20 active:scale-95 dark:bg-[#FF453A]/18 dark:text-[#FF453A] dark:hover:bg-[#FF453A]/28 transition-all cursor-pointer"
                      >
                        <AlertCircle class="w-3.5 h-3.5 shrink-0" />
                        <span>重发</span>
                      </button>

                      <div
                        class="px-4 py-2.5 rounded-[20px] rounded-br-[6px] bg-gradient-to-b from-[#1A8BFF] to-[#007AFF] dark:from-[#1A8FFF] dark:to-[#0A84FF] text-white text-[15px] leading-[1.48] whitespace-pre-wrap break-words shadow-[0_1px_2px_rgba(0,122,255,0.18)] transition-opacity duration-150"
                        :class="{
                          'opacity-75': item.message.sendStatus === 'sending',
                          'opacity-90 ring-1 ring-[#FF3B30]/45 dark:ring-[#FF453A]/50':
                            item.message.sendStatus === 'failed',
                        }"
                      >
                        <MessageContent :content="item.message.content" :is-self="true" />
                      </div>
                    </div>

                    <span
                      v-if="item.message.sendStatus === 'failed' && item.message.failReason"
                      class="mt-1 pr-1 text-[11px] text-[#FF3B30] dark:text-[#FF453A]"
                    >
                      {{ item.message.failReason }}
                    </span>
                  </div>
                </div>
              </template>
            </template>
          </div>
        </div>

        <!-- Floating Frosted Scroll to Bottom Button -->
        <Transition name="popover">
          <button
            v-if="!isAtBottom"
            type="button"
            @click="scrollToBottom(true)"
            class="absolute bottom-3 right-4 sm:right-8 z-10 inline-flex items-center gap-1.5 h-9 px-4 rounded-full ios-glass-popover text-[#1C1C1E] dark:text-[#F5F5F7] ring-1 ring-black/[0.08] dark:ring-white/[0.12] shadow-[0_8px_24px_rgba(0,0,0,0.12)] dark:shadow-[0_10px_28px_rgba(0,0,0,0.55)] hover:scale-[1.02] active:scale-[0.97] text-[13px] font-medium transition-all cursor-pointer"
          >
            <ArrowDown class="w-3.5 h-3.5 text-[#007AFF] dark:text-[#0A84FF]" />
            <span v-if="unreadBelowCount > 0" class="font-mono-tabular">
              {{ unreadBelowCount }} 条新消息
            </span>
            <span v-else>回到底部</span>
          </button>
        </Transition>
        </div>

        <!-- Frosted iOS Bottom Composer -->
        <div
          class="shrink-0 ios-glass-bar border-t border-black/[0.06] dark:border-white/[0.08] px-3 sm:px-7"
          :class="
            isKeyboardOpen
              ? 'py-2'
              : 'pt-2.5 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:py-3'
          "
        >
          <div class="max-w-3xl mx-auto w-full relative">
            <!-- Emoji Popover -->
            <Transition name="popover">
              <div
                v-if="showEmojiPicker"
                class="absolute bottom-full left-0 mb-2.5 sm:mb-3 z-20"
              >
                <EmojiPicker
                  @select="insertEmoji"
                  @close="showEmojiPicker = false"
                />
              </div>
            </Transition>

            <div class="flex items-end gap-2 sm:gap-2.5">
              <!-- Standalone iOS circular emoji trigger -->
              <button
                type="button"
                data-emoji-trigger
                @click="handleToggleEmoji"
                :aria-expanded="showEmojiPicker"
                aria-label="打开常用表情面板"
                title="插入表情"
                class="h-10 w-10 rounded-full inline-flex items-center justify-center shrink-0 transition-all duration-150 active:scale-95 cursor-pointer"
                :class="
                  showEmojiPicker
                    ? 'bg-[#007AFF]/12 text-[#007AFF] dark:bg-[#0A84FF]/20 dark:text-[#0A84FF]'
                    : 'bg-black/[0.05] text-[#636366] hover:bg-black/[0.09] hover:text-[#1C1C1E] dark:bg-white/[0.09] dark:text-[#98989F] dark:hover:bg-white/[0.15] dark:hover:text-[#F5F5F7]'
                "
              >
                <Smile class="w-[19px] h-[19px]" />
              </button>

              <!-- iMessage Pill Input Capsule -->
              <div
                class="flex-1 flex items-end gap-2 rounded-[22px] bg-[#F2F2F7] dark:bg-[#1C1C1E] ring-1 ring-black/[0.08] dark:ring-white/[0.12] focus-within:bg-white dark:focus-within:bg-[#1C1C1E] focus-within:ring-[#007AFF]/70 dark:focus-within:ring-[#0A84FF]/80 pl-3.5 sm:pl-4 pr-1.5 py-1.5 transition-all duration-150"
              >
                <textarea
                  ref="textareaRef"
                  v-model="draft"
                  rows="1"
                  enterkeyhint="send"
                  @focus="handleTextareaFocus"
                  @input="resizeTextarea"
                  @keydown="handleKeydown"
                  @compositionstart="isComposing = true"
                  @compositionend="isComposing = false"
                  :placeholder="
                    status === 'connected'
                      ? '输入消息...'
                      : '正在等待恢复连接...'
                  "
                  class="flex-1 resize-none bg-transparent py-1 text-[16px] sm:text-[15px] leading-[1.45] text-[#1C1C1E] placeholder:text-[#8E8E93] focus:outline-none dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] chat-scroll"
                  :class="isKeyboardOpen ? 'max-h-[96px]' : 'max-h-[136px]'"
                />

                <!-- Counter when approaching limit -->
                <span
                  v-if="draftCharCount > 400"
                  class="text-[11px] font-mono-tabular pb-1.5 pr-1 shrink-0"
                  :class="
                    draftCharCount > MAX_LENGTH
                      ? 'text-[#FF3B30] dark:text-[#FF453A]'
                      : 'text-[#8E8E93]'
                  "
                >
                  {{ draftCharCount }}/{{ MAX_LENGTH }}
                </span>

                <!-- Iconic circular iOS ArrowUp Send Button -->
                <button
                  type="button"
                  @mousedown.prevent
                  @click="handleSend"
                  :disabled="!canSend"
                  aria-label="发送消息"
                  title="发送消息 (Enter)"
                  class="h-8 w-8 rounded-full inline-flex items-center justify-center shrink-0 transition-all duration-150 active:scale-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] cursor-pointer"
                  :class="
                    canSend
                      ? 'bg-[#007AFF] hover:bg-[#0066D6] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white shadow-[0_2px_8px_rgba(0,122,255,0.3)]'
                      : 'bg-black/[0.08] text-[#8E8E93] dark:bg-white/[0.1] dark:text-[#636366] pointer-events-none'
                  "
                >
                  <ArrowUp class="w-4 h-4 stroke-[2.5]" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- Desktop Online Users Sidebar -->
      <aside
        class="hidden md:flex w-[272px] shrink-0 border-l border-black/[0.06] dark:border-white/[0.08] flex-col"
      >
        <OnlineUserList
          :users="onlineUsers"
          :current-user="currentUser"
          :status="status"
        />
      </aside>
    </div>

    <!-- Mobile Online Users Drawer -->
    <Transition name="view-fade">
      <div
        v-if="showMobileUsers"
        class="md:hidden fixed inset-0 z-40 flex justify-end"
      >
        <div
          class="fixed inset-0 bg-black/35 backdrop-blur-xs dark:bg-black/60"
          @click="showMobileUsers = false"
        />
        <aside
          class="relative z-10 w-[290px] max-w-[85vw] h-full border-l border-black/[0.08] dark:border-white/[0.1] shadow-2xl"
        >
          <OnlineUserList
            :users="onlineUsers"
            :current-user="currentUser"
            :status="status"
            :show-close-button="true"
            @close="showMobileUsers = false"
          />
        </aside>
      </div>
    </Transition>
  </div>
</template>
