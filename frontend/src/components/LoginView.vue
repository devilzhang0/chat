<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {ArrowRight, Check, Loader2, MessageSquare, RotateCcw, Trash2, UserPlus} from 'lucide-vue-next';
import ThemeToggle from '@/components/ThemeToggle.vue';
import {
  clearIdentity,
  fetchMe,
  useChatSocket,
  type SavedProfile,
} from '@/composables/useChatSocket';
import {
  AVATAR_COLORS,
  getAvatarBgClass,
  getAvatarInitials,
  type AvatarColorKey,
} from '@/types/chat';

const {login, status, errorMessage, clearError} = useChatSocket();

const savedProfile = ref<SavedProfile | null>(null);
const showQuickLogin = ref<boolean>(false);

const nickname = ref<string>('');
const avatarColor = ref<AvatarColorKey>('cyan');
const localError = ref<string | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);

const trimmedNickname = computed(() => nickname.value.trim());
const charLength = computed(() => Array.from(trimmedNickname.value).length);
const isConnecting = computed(() => status.value === 'connecting');
const displayError = computed(() => localError.value || errorMessage.value);

const canSubmit = computed(
  () => charLength.value >= 2 && charLength.value <= 20 && !isConnecting.value
);

function handleInput(): void {
  if (localError.value) localError.value = null;
  if (errorMessage.value) clearError();
}

async function handleContinueWithCookie(): Promise<void> {
  if (!savedProfile.value || isConnecting.value) return;
  localError.value = null;
  clearError();
  await login(savedProfile.value.nickname, savedProfile.value.avatarColor);
}

function handleSwitchToNewName(): void {
  localError.value = null;
  clearError();
  showQuickLogin.value = false;
  nextTick(() => {
    inputRef.value?.focus();
  });
}

async function handleClearCookie(): Promise<void> {
  await clearIdentity();
  savedProfile.value = null;
  localError.value = null;
  clearError();
  showQuickLogin.value = false;
  nextTick(() => {
    inputRef.value?.focus();
  });
}

function handleBackToQuickLogin(): void {
  if (!savedProfile.value) return;
  localError.value = null;
  clearError();
  showQuickLogin.value = true;
}

async function handleSubmit(): Promise<void> {
  if (charLength.value < 2) {
    localError.value = '请输入至少 2 个字符的昵称';
    inputRef.value?.focus();
    return;
  }
  if (charLength.value > 20) {
    localError.value = '昵称长度不能超过 20 个字符';
    inputRef.value?.focus();
    return;
  }
  localError.value = null;
  await login(trimmedNickname.value, avatarColor.value);
}

onMounted(async () => {
  savedProfile.value = await fetchMe();
  showQuickLogin.value = Boolean(savedProfile.value);
  if (savedProfile.value) {
    avatarColor.value = savedProfile.value.avatarColor;
  }
  if (!showQuickLogin.value) {
    inputRef.value?.focus();
  }
});
</script>

<template>
  <div
    class="min-h-dvh flex flex-col bg-[#F2F2F7] text-[#1C1C1E] dark:bg-black dark:text-[#F5F5F7] transition-colors duration-200"
  >
    <!-- Frosted iOS Top Bar -->
    <header
      class="sticky top-0 z-20 h-16 px-6 sm:px-10 flex items-center justify-between gap-8 ios-glass-bar border-b border-black/[0.06] dark:border-white/[0.08] shrink-0"
    >
      <div class="flex items-center gap-2.5 whitespace-nowrap shrink-0">
        <span
          class="h-8 w-8 rounded-[10px] bg-gradient-to-b from-[#3395FF] to-[#007AFF] dark:from-[#2997FF] dark:to-[#0A84FF] text-white inline-flex items-center justify-center shrink-0 shadow-[0_2px_8px_rgba(0,122,255,0.28)]"
          aria-hidden="true"
        >
          <MessageSquare class="w-4 h-4 fill-white/15" />
        </span>
        <span class="text-[17px] font-semibold tracking-[-0.02em] text-[#1C1C1E] dark:text-[#F5F5F7]">
          Chat
        </span>
      </div>

      <ThemeToggle />
    </header>

    <!-- Main Centered iOS Sheet -->
    <main class="flex-1 flex items-center justify-center px-5 py-12 sm:py-16">
      <div class="w-full max-w-[420px]">
        <div
          class="rounded-[28px] bg-white dark:bg-[#1C1C1E] p-8 sm:p-9 ring-1 ring-black/[0.04] dark:ring-white/[0.08] shadow-[0_24px_60px_-18px_rgba(0,0,0,0.09)] dark:shadow-[0_28px_70px_-16px_rgba(0,0,0,0.7)]"
        >
          <!-- Cookie quick-login prompt -->
          <template v-if="showQuickLogin && savedProfile">
            <div class="flex flex-col items-center text-center mb-7">
              <div
                class="h-[72px] w-[72px] rounded-full flex items-center justify-center text-2xl font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_10px_24px_-8px_rgba(0,0,0,0.22)] mb-4"
                :class="getAvatarBgClass(savedProfile.avatarColor)"
                aria-hidden="true"
              >
                {{ getAvatarInitials(savedProfile.nickname) }}
              </div>
              <h1
                class="text-[22px] font-semibold tracking-[-0.025em] text-[#1C1C1E] dark:text-[#F5F5F7] text-balance"
              >
                是否继续登录？
              </h1>
              <p class="mt-1.5 text-[14px] text-[#636366] dark:text-[#98989F]">
                检测到上次的登录记录
              </p>
            </div>

            <div
              class="mb-6 px-4 py-3.5 rounded-[16px] bg-[#F2F2F7] dark:bg-[#2C2C2E] flex items-center justify-between gap-3"
            >
              <span class="text-[13px] text-[#636366] dark:text-[#98989F] shrink-0">
                上次使用昵称
              </span>
              <span
                class="text-[15px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7] truncate"
                :title="savedProfile.nickname"
              >
                {{ savedProfile.nickname }}
              </span>
            </div>

            <p
              v-if="displayError"
              role="alert"
              class="mb-4 text-[13px] text-[#FF3B30] dark:text-[#FF453A] text-center"
            >
              {{ displayError }}
            </p>

            <div class="space-y-3">
              <button
                type="button"
                @click="handleContinueWithCookie"
                :disabled="isConnecting"
                class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-45 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[#1C1C1E] cursor-pointer"
              >
                <Loader2 v-if="isConnecting" class="w-4 h-4 animate-spin shrink-0" />
                <span class="truncate">
                  {{ isConnecting ? '正在进入大厅...' : `以「${savedProfile.nickname}」继续登录` }}
                </span>
                <ArrowRight v-if="!isConnecting" class="w-4 h-4 shrink-0" />
              </button>

              <div class="grid grid-cols-2 gap-2.5 pt-0.5">
                <button
                  type="button"
                  @click="handleSwitchToNewName"
                  :disabled="isConnecting"
                  class="h-11 px-3 rounded-[14px] bg-black/[0.05] text-[#1C1C1E] hover:bg-black/[0.08] active:scale-[0.98] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-white/[0.14] text-[13px] font-medium inline-flex items-center justify-center gap-1.5 whitespace-nowrap transition-all duration-150 disabled:opacity-45 cursor-pointer"
                >
                  <UserPlus class="w-3.5 h-3.5 shrink-0" />
                  <span>使用其他昵称</span>
                </button>

                <button
                  type="button"
                  @click="handleClearCookie"
                  :disabled="isConnecting"
                  class="h-11 px-3 rounded-[14px] bg-black/[0.05] text-[#636366] hover:bg-[#FF3B30]/10 hover:text-[#D70015] active:scale-[0.98] dark:bg-white/[0.09] dark:text-[#98989F] dark:hover:bg-[#FF453A]/15 dark:hover:text-[#FF453A] text-[13px] font-medium inline-flex items-center justify-center gap-1.5 whitespace-nowrap transition-all duration-150 disabled:opacity-45 cursor-pointer"
                >
                  <Trash2 class="w-3.5 h-3.5 shrink-0" />
                  <span>清除记录</span>
                </button>
              </div>
            </div>
          </template>

          <!-- Standard login form -->
          <template v-else>
            <!-- Centered Live Avatar Preview & Title -->
            <div class="flex flex-col items-center text-center mb-8">
              <div
                class="h-[72px] w-[72px] rounded-full flex items-center justify-center text-2xl font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_10px_24px_-8px_rgba(0,0,0,0.22)] transition-all duration-200 mb-4"
                :class="getAvatarBgClass(avatarColor)"
                aria-hidden="true"
              >
                {{ trimmedNickname ? getAvatarInitials(trimmedNickname) : '聊' }}
              </div>
              <h1
                class="text-[22px] font-semibold tracking-[-0.025em] text-[#1C1C1E] dark:text-[#F5F5F7] text-balance"
              >
                进入公共大厅
              </h1>
              <p class="mt-1.5 text-[14px] text-[#636366] dark:text-[#98989F]">
                设置昵称与头像色，即刻开始实时对话
              </p>
            </div>

            <form @submit.prevent="handleSubmit" class="space-y-6" novalidate>
              <!-- Nickname field -->
              <div>
                <div class="flex items-center justify-between mb-2 px-1">
                  <label
                    for="nickname-input"
                    class="text-[13px] font-medium text-[#636366] dark:text-[#98989F]"
                  >
                    你的昵称
                  </label>
                  <span
                    class="text-xs font-mono-tabular"
                    :class="
                      charLength > 20
                        ? 'text-[#FF3B30] dark:text-[#FF453A]'
                        : 'text-[#8E8E93]'
                    "
                  >
                    {{ charLength }}/20
                  </span>
                </div>

                <input
                  id="nickname-input"
                  ref="inputRef"
                  v-model="nickname"
                  @input="handleInput"
                  type="text"
                  maxlength="24"
                  autocomplete="nickname"
                  placeholder="例如：林浅、Alex"
                  :disabled="isConnecting"
                  class="w-full h-12 px-4 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                />

                <p
                  v-if="displayError"
                  role="alert"
                  class="mt-2 px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]"
                >
                  {{ displayError }}
                </p>
              </div>

              <!-- Avatar color picker -->
              <div>
                <span class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-3 px-1">
                  头像颜色
                </span>
                <div
                  class="flex items-center justify-between gap-2 px-1"
                  role="radiogroup"
                  aria-label="选择头像颜色"
                >
                  <button
                    v-for="item in AVATAR_COLORS"
                    :key="item.key"
                    type="button"
                    role="radio"
                    :aria-checked="avatarColor === item.key"
                    :aria-label="item.label"
                    :title="item.label"
                    :disabled="isConnecting"
                    @click="avatarColor = item.key"
                    class="h-10 w-10 rounded-full inline-flex items-center justify-center transition-all duration-150 hover:scale-105 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[#007AFF] dark:focus-visible:ring-offset-[#1C1C1E] cursor-pointer shadow-[inset_0_1px_0_rgba(255,255,255,0.25)]"
                    :class="[
                      item.swatchClass,
                      avatarColor === item.key
                        ? 'ring-2 ring-offset-2 ring-offset-white dark:ring-offset-[#1C1C1E] scale-105 ' + item.ringClass
                        : 'opacity-85 hover:opacity-100',
                    ]"
                  >
                    <Check v-if="avatarColor === item.key" class="w-4 h-4 text-white stroke-[2.5]" />
                  </button>
                </div>
              </div>

              <!-- Primary CTA -->
              <button
                type="submit"
                :disabled="!canSubmit"
                class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-40 disabled:shadow-none disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#007AFF] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[#1C1C1E] cursor-pointer"
              >
                <Loader2 v-if="isConnecting" class="w-4 h-4 animate-spin shrink-0" />
                <span>{{ isConnecting ? '正在进入大厅...' : '进入聊天大厅' }}</span>
                <ArrowRight v-if="!isConnecting" class="w-4 h-4 shrink-0" />
              </button>

              <!-- Return to cookie prompt if cookie exists -->
              <div v-if="savedProfile" class="pt-1 text-center">
                <button
                  type="button"
                  @click="handleBackToQuickLogin"
                  :disabled="isConnecting"
                  class="inline-flex items-center gap-1.5 text-[13px] font-medium text-[#007AFF] hover:text-[#0066D6] dark:text-[#0A84FF] dark:hover:text-[#409CFF] transition-colors cursor-pointer"
                >
                  <RotateCcw class="w-3.5 h-3.5 shrink-0" />
                  <span>使用上次记录的「{{ savedProfile.nickname }}」继续登录</span>
                </button>
              </div>
            </form>
          </template>
        </div>
      </div>
    </main>
  </div>
</template>
