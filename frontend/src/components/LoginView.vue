<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Clock,
  DoorOpen,
  Eye,
  EyeOff,
  Globe,
  Hash,
  Loader2,
  Lock,
  MessageSquare,
  Plus,
  RotateCcw,
  Trash2,
  UserPlus,
  Users,
  X,
} from 'lucide-vue-next';
import ThemeToggle from '@/components/ThemeToggle.vue';
import {
  clearIdentity,
  clearRecentRoomsCookie,
  createRoomApi,
  fetchMe,
  fetchRecentRoomsApi,
  getRoomCodeFromUrl,
  lookupRoomApi,
  removeRecentRoomCookie,
  setRoomCodeInUrl,
  useChatSocket,
  type SavedProfile,
} from '@/composables/useChatSocket';
import {
  AVATAR_COLORS,
  getAvatarBgClass,
  getAvatarInitials,
  type AvatarColorKey,
  type RoomInfo,
} from '@/types/chat';

const {joinRoom, lastProfile, status, errorMessage, clearError, clearLastProfile} = useChatSocket();

// 入口模式：普通流程 vs 分享链接流程
const inviteCode = ref<string | null>(null);
const inviteLoading = ref(false);
const inviteRoom = ref<RoomInfo | null>(null);
const inviteError = ref<string | null>(null);
const invitePassword = ref('');
const showInvitePassword = ref(false);

// 普通流程步骤：identity（设置昵称） -> room（创建/加入房间）
const step = ref<'identity' | 'room'>('identity');
const actionTab = ref<'join' | 'create'>('join');

// 身份信息
const savedProfile = ref<SavedProfile | null>(null);
const showQuickLogin = ref(false);
const nickname = ref('');
const avatarColor = ref<AvatarColorKey>('cyan');
const localError = ref<string | null>(null);

// 加入房间表单
const joinRoomCode = ref('');
const lookedUpRoom = ref<RoomInfo | null>(null);
const joinPassword = ref('');
const showJoinPassword = ref(false);
const isLookingUp = ref(false);

// Cookie 记录的进过的房间
const recentRooms = ref<RoomInfo[]>([]);
const quickPrivateRoom = ref<RoomInfo | null>(null);
const quickPrivatePassword = ref('');
const showQuickPrivatePassword = ref(false);
const quickPrivatePwdInputRef = ref<HTMLInputElement | null>(null);

// 创建房间表单
const createRoomName = ref('');
const createIsPrivate = ref(false);
const createPassword = ref('');
const showCreatePassword = ref(false);
const isCreating = ref(false);

// DOM refs
const nicknameInputRef = ref<HTMLInputElement | null>(null);
const joinCodeInputRef = ref<HTMLInputElement | null>(null);
const joinPwdInputRef = ref<HTMLInputElement | null>(null);
const createNameInputRef = ref<HTMLInputElement | null>(null);
const invitePwdInputRef = ref<HTMLInputElement | null>(null);

const trimmedNickname = computed(() => nickname.value.trim());
const nicknameCharLen = computed(() => Array.from(trimmedNickname.value).length);
const createNameTrimmed = computed(() => createRoomName.value.trim());
const createNameLen = computed(() => Array.from(createNameTrimmed.value).length);
const createPwdLen = computed(() => Array.from(createPassword.value).length);

const isBusy = computed(
  () => status.value === 'connecting' || isLookingUp.value || isCreating.value
);
const displayError = computed(() => localError.value || errorMessage.value);

const canConfirmIdentity = computed(
  () => nicknameCharLen.value >= 2 && nicknameCharLen.value <= 20 && !isBusy.value
);

const canSubmitInvite = computed(() => {
  if (!inviteRoom.value || isBusy.value) return false;
  if (nicknameCharLen.value < 2 || nicknameCharLen.value > 20) return false;
  if (inviteRoom.value.isPrivate && invitePassword.value.length === 0) return false;
  return true;
});

const canSubmitCreate = computed(() => {
  if (isBusy.value) return false;
  if (createNameLen.value < 1 || createNameLen.value > 24) return false;
  if (createIsPrivate.value && (createPwdLen.value < 4 || createPwdLen.value > 20)) return false;
  return true;
});

function clearAllErrors(): void {
  localError.value = null;
  if (errorMessage.value) clearError();
}

// 修改房间号时清除上次查询出的私密房状态
watch(joinRoomCode, (val) => {
  if (lookedUpRoom.value && lookedUpRoom.value.code !== val.trim()) {
    lookedUpRoom.value = null;
    joinPassword.value = '';
  }
  clearAllErrors();
});

watch(actionTab, (tab) => {
  clearAllErrors();
  nextTick(() => {
    if (tab === 'join') {
      joinCodeInputRef.value?.focus();
    } else {
      createNameInputRef.value?.focus();
    }
  });
});

// ================= 身份设置步骤 =================

function handleContinueWithCookie(): void {
  if (!savedProfile.value) return;
  clearAllErrors();
  nickname.value = savedProfile.value.nickname;
  avatarColor.value = savedProfile.value.avatarColor;
  step.value = 'room';
  nextTick(() => {
    joinCodeInputRef.value?.focus();
  });
}

function handleSwitchToNewName(): void {
  clearAllErrors();
  showQuickLogin.value = false;
  nextTick(() => {
    nicknameInputRef.value?.focus();
  });
}

async function handleClearCookie(): Promise<void> {
  await clearIdentity();
  clearRecentRoomsCookie();
  savedProfile.value = null;
  recentRooms.value = [];
  quickPrivateRoom.value = null;
  quickPrivatePassword.value = '';
  clearLastProfile();
  nickname.value = '';
  clearAllErrors();
  showQuickLogin.value = false;
  nextTick(() => {
    nicknameInputRef.value?.focus();
  });
}

function handleBackToQuickLogin(): void {
  if (!savedProfile.value) return;
  clearAllErrors();
  showQuickLogin.value = true;
}

function handleConfirmIdentity(): void {
  if (nicknameCharLen.value < 2) {
    localError.value = '请输入至少 2 个字符的昵称';
    nicknameInputRef.value?.focus();
    return;
  }
  if (nicknameCharLen.value > 20) {
    localError.value = '昵称长度不能超过 20 个字符';
    nicknameInputRef.value?.focus();
    return;
  }
  clearAllErrors();
  step.value = 'room';
  nextTick(() => {
    if (actionTab.value === 'join') {
      joinCodeInputRef.value?.focus();
    } else {
      createNameInputRef.value?.focus();
    }
  });
}

function handleEditIdentity(): void {
  clearAllErrors();
  showQuickLogin.value = false;
  step.value = 'identity';
  nextTick(() => {
    nicknameInputRef.value?.focus();
  });
}

// ================= 加入房间 =================

async function handleJoinRoomSubmit(): Promise<void> {
  if (isBusy.value) return;
  clearAllErrors();

  const code = joinRoomCode.value.trim();
  if (!code) {
    localError.value = '请输入房间号';
    joinCodeInputRef.value?.focus();
    return;
  }

  // 已查到私密房 → 校验密码并进入
  if (lookedUpRoom.value && lookedUpRoom.value.code === code && lookedUpRoom.value.isPrivate) {
    if (!joinPassword.value) {
      localError.value = '请输入房间密码';
      joinPwdInputRef.value?.focus();
      return;
    }
    const res = await joinRoom({
      nickname: trimmedNickname.value,
      avatarColor: avatarColor.value,
      roomCode: code,
      password: joinPassword.value,
    });
    if (!res.ok) {
      localError.value = res.message || '进入房间失败';
      if (res.code === 'INVALID_PASSWORD') {
        nextTick(() => joinPwdInputRef.value?.focus());
      }
    }
    return;
  }

  // 查询房间是否存在及公开/私密属性
  isLookingUp.value = true;
  const lookup = await lookupRoomApi(code);
  isLookingUp.value = false;

  if (!lookup.ok) {
    localError.value = lookup.message;
    return;
  }

  if (!lookup.result.exists || !lookup.result.code) {
    localError.value = '房间未创建';
    joinCodeInputRef.value?.focus();
    return;
  }

  const found: RoomInfo = {
    code: lookup.result.code,
    name: lookup.result.name || `房间 ${lookup.result.code}`,
    isPrivate: Boolean(lookup.result.isPrivate),
    onlineCount: lookup.result.onlineCount ?? 0,
  };

  if (found.isPrivate) {
    lookedUpRoom.value = found;
    joinPassword.value = '';
    nextTick(() => {
      joinPwdInputRef.value?.focus();
    });
    return;
  }

  // 公开房：直接进入
  const res = await joinRoom({
    nickname: trimmedNickname.value,
    avatarColor: avatarColor.value,
    roomCode: found.code,
  });
  if (!res.ok) {
    localError.value = res.message || '进入房间失败';
  }
}

// ================= Cookie 历史房间快捷进入 =================

function resolveIdentityForQuickJoin(useSavedCookie: boolean): {nickname: string; avatarColor: AvatarColorKey} | null {
  if (useSavedCookie && savedProfile.value) {
    nickname.value = savedProfile.value.nickname;
    avatarColor.value = savedProfile.value.avatarColor;
    return {
      nickname: savedProfile.value.nickname,
      avatarColor: savedProfile.value.avatarColor,
    };
  }
  if (nicknameCharLen.value < 2 || nicknameCharLen.value > 20) {
    localError.value = '请先输入 2 到 20 个字符的昵称';
    nextTick(() => nicknameInputRef.value?.focus());
    return null;
  }
  return {
    nickname: trimmedNickname.value,
    avatarColor: avatarColor.value,
  };
}

async function handleSelectRecentRoom(room: RoomInfo, useSavedCookie = false): Promise<void> {
  if (isBusy.value) return;
  clearAllErrors();

  const identity = resolveIdentityForQuickJoin(useSavedCookie);
  if (!identity) return;

  if (room.isPrivate) {
    if (step.value === 'room') {
      joinRoomCode.value = room.code;
      lookedUpRoom.value = room;
      joinPassword.value = '';
      nextTick(() => joinPwdInputRef.value?.focus());
    } else {
      quickPrivateRoom.value = room;
      quickPrivatePassword.value = '';
      nextTick(() => quickPrivatePwdInputRef.value?.focus());
    }
    return;
  }

  // 公开房直接快速进入
  const res = await joinRoom({
    nickname: identity.nickname,
    avatarColor: identity.avatarColor,
    roomCode: room.code,
  });
  if (!res.ok) {
    localError.value = res.message || '进入房间失败';
    if (res.code === 'ROOM_NOT_FOUND') {
      handleRemoveRecentRoom(room.code);
    }
  }
}

async function handleQuickPrivateSubmit(useSavedCookie = false): Promise<void> {
  if (!quickPrivateRoom.value || isBusy.value) return;
  clearAllErrors();

  const identity = resolveIdentityForQuickJoin(useSavedCookie);
  if (!identity) return;

  if (!quickPrivatePassword.value) {
    localError.value = '请输入房间密码';
    nextTick(() => quickPrivatePwdInputRef.value?.focus());
    return;
  }

  const target = quickPrivateRoom.value;
  const res = await joinRoom({
    nickname: identity.nickname,
    avatarColor: identity.avatarColor,
    roomCode: target.code,
    password: quickPrivatePassword.value,
  });
  if (!res.ok) {
    localError.value = res.message || '进入房间失败';
    if (res.code === 'INVALID_PASSWORD') {
      nextTick(() => quickPrivatePwdInputRef.value?.focus());
    } else if (res.code === 'ROOM_NOT_FOUND') {
      handleRemoveRecentRoom(target.code);
    }
  }
}

function handleRemoveRecentRoom(code: string): void {
  removeRecentRoomCookie(code);
  recentRooms.value = recentRooms.value.filter((r) => r.code !== code);
  if (quickPrivateRoom.value?.code === code) {
    quickPrivateRoom.value = null;
    quickPrivatePassword.value = '';
  }
  if (lookedUpRoom.value?.code === code) {
    lookedUpRoom.value = null;
    joinPassword.value = '';
  }
}

// ================= 创建房间 =================

async function handleCreateRoomSubmit(): Promise<void> {
  if (isBusy.value) return;
  clearAllErrors();

  if (createNameLen.value < 1 || createNameLen.value > 24) {
    localError.value = '房间名称长度需在 1 到 24 个字符之间';
    createNameInputRef.value?.focus();
    return;
  }
  if (createIsPrivate.value) {
    if (!createPassword.value.trim() || createPwdLen.value < 4 || createPwdLen.value > 20) {
      localError.value = '私密房间密码长度需在 4 到 20 个字符之间';
      return;
    }
  }

  isCreating.value = true;
  const created = await createRoomApi({
    name: createNameTrimmed.value,
    isPrivate: createIsPrivate.value,
    password: createIsPrivate.value ? createPassword.value : undefined,
  });
  isCreating.value = false;

  if (!created.ok) {
    localError.value = created.message;
    return;
  }

  const res = await joinRoom({
    nickname: trimmedNickname.value,
    avatarColor: avatarColor.value,
    roomCode: created.room.code,
    password: createIsPrivate.value ? createPassword.value : undefined,
  });
  if (!res.ok) {
    localError.value = res.message || '房间已创建，但进入失败，请稍后重试';
  }
}

// ================= 分享链接直接入房 =================

async function loadInviteRoom(code: string): Promise<void> {
  inviteLoading.value = true;
  inviteError.value = null;
  inviteRoom.value = null;

  const lookup = await lookupRoomApi(code);
  inviteLoading.value = false;

  if (!lookup.ok) {
    inviteError.value = lookup.message;
    return;
  }

  if (!lookup.result.exists || !lookup.result.code) {
    inviteError.value = `房间 #${code} 未创建或分享链接已失效`;
    return;
  }

  inviteRoom.value = {
    code: lookup.result.code,
    name: lookup.result.name || `房间 ${lookup.result.code}`,
    isPrivate: Boolean(lookup.result.isPrivate),
    onlineCount: lookup.result.onlineCount ?? 0,
  };

  nextTick(() => {
    if (!nickname.value.trim()) {
      nicknameInputRef.value?.focus();
    } else if (inviteRoom.value?.isPrivate) {
      invitePwdInputRef.value?.focus();
    }
  });
}

async function handleInviteSubmit(): Promise<void> {
  if (!inviteRoom.value || isBusy.value) return;
  clearAllErrors();

  if (nicknameCharLen.value < 2 || nicknameCharLen.value > 20) {
    localError.value = '昵称长度需在 2 到 20 个字符之间';
    nicknameInputRef.value?.focus();
    return;
  }
  if (inviteRoom.value.isPrivate && !invitePassword.value) {
    localError.value = '该房间为私密房间，请输入房间密码';
    invitePwdInputRef.value?.focus();
    return;
  }

  const res = await joinRoom({
    nickname: trimmedNickname.value,
    avatarColor: avatarColor.value,
    roomCode: inviteRoom.value.code,
    password: inviteRoom.value.isPrivate ? invitePassword.value : undefined,
  });

  if (!res.ok) {
    localError.value = res.message || '进入房间失败';
    if (res.code === 'INVALID_PASSWORD') {
      nextTick(() => invitePwdInputRef.value?.focus());
    }
  }
}

function exitInviteMode(): void {
  inviteCode.value = null;
  inviteRoom.value = null;
  inviteError.value = null;
  invitePassword.value = '';
  clearAllErrors();
  setRoomCodeInUrl(null);
  if (savedProfile.value && !nickname.value.trim()) {
    showQuickLogin.value = true;
  } else {
    showQuickLogin.value = false;
  }
  step.value = 'identity';
  nextTick(() => {
    if (!showQuickLogin.value) {
      nicknameInputRef.value?.focus();
    }
  });
}

// ================= 初始化 =================

onMounted(async () => {
  const urlCode = getRoomCodeFromUrl();
  const [profileFromCookie, recentList] = await Promise.all([
    fetchMe(),
    fetchRecentRoomsApi(),
  ]);
  savedProfile.value = profileFromCookie;
  recentRooms.value = recentList;

  if (urlCode) {
    // 分享链接模式：先加载房间信息，进入前必须输入/确认用户名
    inviteCode.value = urlCode;
    const prefill = lastProfile.value || profileFromCookie;
    if (prefill) {
      nickname.value = prefill.nickname;
      avatarColor.value = prefill.avatarColor;
    }
    await loadInviteRoom(urlCode);
    return;
  }

  // 普通模式：如果是刚从房间离开回到此页，保留身份直接进入选房步骤
  if (lastProfile.value) {
    nickname.value = lastProfile.value.nickname;
    avatarColor.value = lastProfile.value.avatarColor;
    step.value = 'room';
    nextTick(() => {
      joinCodeInputRef.value?.focus();
    });
    return;
  }

  showQuickLogin.value = Boolean(profileFromCookie);
  if (profileFromCookie) {
    avatarColor.value = profileFromCookie.avatarColor;
  } else {
    nextTick(() => {
      nicknameInputRef.value?.focus();
    });
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
          MyChat
        </span>
      </div>

      <ThemeToggle />
    </header>

    <!-- Main Centered iOS Sheet -->
    <main class="flex-1 flex items-center justify-center px-5 py-10 sm:py-14">
      <div class="w-full max-w-[440px]">
        <div
          class="rounded-[28px] bg-white dark:bg-[#1C1C1E] p-7 sm:p-9 ring-1 ring-black/[0.04] dark:ring-white/[0.08] shadow-[0_24px_60px_-18px_rgba(0,0,0,0.09)] dark:shadow-[0_28px_70px_-16px_rgba(0,0,0,0.7)]"
        >
          <!-- ========================================================= -->
          <!-- 模式一：通过分享链接 (?room=code) 进入                       -->
          <!-- ========================================================= -->
          <template v-if="inviteCode">
            <!-- 查询中 -->
            <div v-if="inviteLoading" class="py-12 flex flex-col items-center text-center">
              <Loader2 class="w-8 h-8 text-[#007AFF] dark:text-[#0A84FF] animate-spin mb-4" />
              <p class="text-[15px] font-medium text-[#1C1C1E] dark:text-[#F5F5F7]">
                正在获取房间信息...
              </p>
              <p class="mt-1 text-[13px] text-[#8E8E93] font-mono-tabular">
                房间号 #{{ inviteCode }}
              </p>
            </div>

            <!-- 房间未创建或获取失败 -->
            <div v-else-if="inviteError" class="py-6 flex flex-col items-center text-center">
              <div
                class="h-14 w-14 rounded-[18px] bg-[#FF3B30]/10 text-[#FF3B30] dark:bg-[#FF453A]/15 dark:text-[#FF453A] flex items-center justify-center mb-4"
              >
                <DoorOpen class="w-6 h-6" />
              </div>
              <h1 class="text-[20px] font-semibold tracking-[-0.02em] text-[#1C1C1E] dark:text-[#F5F5F7]">
                无法进入房间
              </h1>
              <p class="mt-2 text-[14px] text-[#636366] dark:text-[#98989F]">
                {{ inviteError }}
              </p>
              <button
                type="button"
                @click="exitInviteMode"
                class="mt-6 w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <span>返回首页创建或加入房间</span>
                <ArrowRight class="w-4 h-4" />
              </button>
            </div>

            <!-- 房间存在：进入前必须输入/确认用户名（私密房需密码） -->
            <template v-else-if="inviteRoom">
              <!-- 受邀房间横幅 -->
              <div
                class="mb-6 p-4 rounded-[18px] bg-[#F2F2F7] dark:bg-[#2C2C2E] flex items-center gap-3.5"
              >
                <div
                  class="h-11 w-11 rounded-[14px] flex items-center justify-center shrink-0"
                  :class="
                    inviteRoom.isPrivate
                      ? 'bg-[#FF9500]/15 text-[#C96B00] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A]'
                      : 'bg-[#007AFF]/12 text-[#007AFF] dark:bg-[#0A84FF]/20 dark:text-[#0A84FF]'
                  "
                >
                  <Lock v-if="inviteRoom.isPrivate" class="w-5 h-5" />
                  <Globe v-else class="w-5 h-5" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2 min-w-0">
                    <h2
                      class="text-[16px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7] truncate"
                      :title="inviteRoom.name"
                    >
                      {{ inviteRoom.name }}
                    </h2>
                    <span
                      class="px-2 py-0.5 rounded-full text-[11px] font-medium shrink-0"
                      :class="
                        inviteRoom.isPrivate
                          ? 'bg-[#FF9500]/15 text-[#B25000] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A]'
                          : 'bg-[#34C759]/15 text-[#248A3D] dark:bg-[#30D158]/20 dark:text-[#30D158]'
                      "
                    >
                      {{ inviteRoom.isPrivate ? '私密' : '公开' }}
                    </span>
                  </div>
                  <div class="mt-0.5 flex items-center gap-2.5 text-[12px] text-[#636366] dark:text-[#98989F]">
                    <span class="font-mono-tabular">房间号 #{{ inviteRoom.code }}</span>
                    <span aria-hidden="true">·</span>
                    <span class="inline-flex items-center gap-1 font-mono-tabular">
                      <Users class="w-3 h-3" />
                      {{ inviteRoom.onlineCount ?? 0 }} 人在线
                    </span>
                  </div>
                </div>
              </div>

              <div class="flex flex-col items-center text-center mb-6">
                <div
                  class="h-14 w-14 rounded-full flex items-center justify-center text-xl font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_8px_20px_-6px_rgba(0,0,0,0.2)] transition-all duration-200 mb-3"
                  :class="getAvatarBgClass(avatarColor)"
                  aria-hidden="true"
                >
                  {{ trimmedNickname ? getAvatarInitials(trimmedNickname) : '聊' }}
                </div>
                <h1 class="text-[20px] font-semibold tracking-[-0.02em] text-[#1C1C1E] dark:text-[#F5F5F7]">
                  输入用户名进入房间
                </h1>
              </div>

              <form @submit.prevent="handleInviteSubmit" class="space-y-5" novalidate>
                <div>
                  <div class="flex items-center justify-between mb-2 px-1">
                    <label
                      for="invite-nickname-input"
                      class="text-[13px] font-medium text-[#636366] dark:text-[#98989F]"
                    >
                      你的昵称
                    </label>
                    <span
                      class="text-xs font-mono-tabular"
                      :class="nicknameCharLen > 20 ? 'text-[#FF3B30] dark:text-[#FF453A]' : 'text-[#8E8E93]'"
                    >
                      {{ nicknameCharLen }}/20
                    </span>
                  </div>
                  <input
                    id="invite-nickname-input"
                    ref="nicknameInputRef"
                    v-model="nickname"
                    @input="clearAllErrors"
                    type="text"
                    maxlength="24"
                    autocomplete="nickname"
                    placeholder="例如：林浅、Alex"
                    :disabled="isBusy"
                    class="w-full h-12 px-4 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                  />
                </div>

                <div>
                  <span class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-2.5 px-1">
                    头像颜色
                  </span>
                  <div class="flex items-center justify-between gap-2 px-1" role="radiogroup" aria-label="选择头像颜色">
                    <button
                      v-for="item in AVATAR_COLORS"
                      :key="item.key"
                      type="button"
                      role="radio"
                      :aria-checked="avatarColor === item.key"
                      :aria-label="item.label"
                      :title="item.label"
                      :disabled="isBusy"
                      @click="avatarColor = item.key"
                      class="h-9 w-9 rounded-full inline-flex items-center justify-center transition-all duration-150 hover:scale-105 active:scale-95 cursor-pointer shadow-[inset_0_1px_0_rgba(255,255,255,0.25)]"
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

                <div v-if="inviteRoom.isPrivate">
                  <label
                    for="invite-password-input"
                    class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-2 px-1"
                  >
                    房间密码
                  </label>
                  <div class="relative">
                    <input
                      id="invite-password-input"
                      ref="invitePwdInputRef"
                      v-model="invitePassword"
                      @input="clearAllErrors"
                      :type="showInvitePassword ? 'text' : 'password'"
                      maxlength="20"
                      autocomplete="off"
                      placeholder="请输入私密房间密码"
                      :disabled="isBusy"
                      class="w-full h-12 pl-4 pr-11 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                    />
                    <button
                      type="button"
                      @click="showInvitePassword = !showInvitePassword"
                      class="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
                      :aria-label="showInvitePassword ? '隐藏密码' : '显示密码'"
                    >
                      <EyeOff v-if="showInvitePassword" class="w-4 h-4" />
                      <Eye v-else class="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <p v-if="displayError" role="alert" class="px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]">
                  {{ displayError }}
                </p>

                <button
                  type="submit"
                  :disabled="!canSubmitInvite"
                  class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-40 disabled:shadow-none disabled:pointer-events-none cursor-pointer"
                >
                  <Loader2 v-if="isBusy" class="w-4 h-4 animate-spin shrink-0" />
                  <span>{{ isBusy ? '正在进入房间...' : '进入房间' }}</span>
                  <ArrowRight v-if="!isBusy" class="w-4 h-4 shrink-0" />
                </button>

                <div class="text-center pt-1">
                  <button
                    type="button"
                    @click="exitInviteMode"
                    class="text-[13px] font-medium text-[#636366] hover:text-[#1C1C1E] dark:text-[#98989F] dark:hover:text-[#F5F5F7] transition-colors cursor-pointer"
                  >
                    返回首页，创建或加入其他房间
                  </button>
                </div>
              </form>
            </template>
          </template>

          <!-- ========================================================= -->
          <!-- 模式二：普通入口（Step 1 设置昵称 → Step 2 创建/加入房间）   -->
          <!-- ========================================================= -->
          <template v-else-if="step === 'identity'">
            <!-- Cookie 快捷确认 -->
            <template v-if="showQuickLogin && savedProfile">
              <div class="flex flex-col items-center text-center mb-7">
                <div
                  class="h-[72px] w-[72px] rounded-full flex items-center justify-center text-2xl font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_10px_24px_-8px_rgba(0,0,0,0.22)] mb-4"
                  :class="getAvatarBgClass(savedProfile.avatarColor)"
                  aria-hidden="true"
                >
                  {{ getAvatarInitials(savedProfile.nickname) }}
                </div>
                <h1 class="text-[22px] font-semibold tracking-[-0.025em] text-[#1C1C1E] dark:text-[#F5F5F7]">
                  欢迎回来
                </h1>
                <p class="mt-1.5 text-[14px] text-[#636366] dark:text-[#98989F]">
                  确认昵称后即可创建或加入聊天室
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

              <div class="space-y-3">
                <button
                  type="button"
                  @click="handleContinueWithCookie"
                  class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 cursor-pointer"
                >
                  <span class="truncate">以「{{ savedProfile.nickname }}」继续</span>
                  <ArrowRight class="w-4 h-4 shrink-0" />
                </button>

                <div class="grid grid-cols-2 gap-2.5 pt-0.5">
                  <button
                    type="button"
                    @click="handleSwitchToNewName"
                    :disabled="isBusy"
                    class="h-11 px-3 rounded-[14px] bg-black/[0.05] text-[#1C1C1E] hover:bg-black/[0.08] active:scale-[0.98] dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:hover:bg-white/[0.14] text-[13px] font-medium inline-flex items-center justify-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
                  >
                    <UserPlus class="w-3.5 h-3.5 shrink-0" />
                    <span>使用其他昵称</span>
                  </button>

                  <button
                    type="button"
                    @click="handleClearCookie"
                    :disabled="isBusy"
                    class="h-11 px-3 rounded-[14px] bg-black/[0.05] text-[#636366] hover:bg-[#FF3B30]/10 hover:text-[#D70015] active:scale-[0.98] dark:bg-white/[0.09] dark:text-[#98989F] dark:hover:bg-[#FF453A]/15 dark:hover:text-[#FF453A] text-[13px] font-medium inline-flex items-center justify-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
                  >
                    <Trash2 class="w-3.5 h-3.5 shrink-0" />
                    <span>清除记录</span>
                  </button>
                </div>
              </div>

              <!-- Cookie 中保存的最近进过的房间：点击快速进入（私密房需输密码） -->
              <div
                v-if="recentRooms.length > 0"
                class="mt-6 pt-5 border-t border-black/[0.06] dark:border-white/[0.08]"
              >
                <div class="flex items-center justify-between mb-2.5 px-1">
                  <span class="inline-flex items-center gap-1.5 text-[12px] font-medium text-[#636366] dark:text-[#98989F]">
                    <Clock class="w-3.5 h-3.5" />
                    <span>最近进过的房间</span>
                  </span>
                  <span class="text-[11px] text-[#8E8E93]">点击快速进入</span>
                </div>

                <div class="space-y-2 max-h-[210px] overflow-y-auto chat-scroll pr-0.5">
                  <div
                    v-for="room in recentRooms"
                    :key="room.code"
                    class="rounded-[14px] transition-all"
                    :class="
                      quickPrivateRoom?.code === room.code
                        ? 'bg-[#FF9500]/[0.08] ring-1 ring-[#FF9500]/25 dark:bg-[#FF9F0A]/[0.1] dark:ring-[#FF9F0A]/30 p-3'
                        : 'bg-[#F2F2F7] hover:bg-[#E5E5EA] dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C]'
                    "
                  >
                    <div class="flex items-center gap-2.5" :class="quickPrivateRoom?.code === room.code ? '' : 'px-3 py-2.5'">
                      <button
                        type="button"
                        @click="handleSelectRecentRoom(room, true)"
                        :disabled="isBusy"
                        class="flex-1 flex items-center gap-2.5 min-w-0 text-left cursor-pointer disabled:opacity-50"
                      >
                        <span
                          class="h-8 w-8 rounded-[10px] flex items-center justify-center shrink-0"
                          :class="
                            room.isPrivate
                              ? 'bg-[#FF9500]/15 text-[#C96B00] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A]'
                              : 'bg-[#007AFF]/12 text-[#007AFF] dark:bg-[#0A84FF]/20 dark:text-[#0A84FF]'
                          "
                        >
                          <Lock v-if="room.isPrivate" class="w-3.5 h-3.5" />
                          <Globe v-else class="w-3.5 h-3.5" />
                        </span>
                        <div class="min-w-0 flex-1">
                          <div class="flex items-center gap-1.5 min-w-0">
                            <span class="text-[14px] font-medium text-[#1C1C1E] dark:text-[#F5F5F7] truncate">
                              {{ room.name }}
                            </span>
                            <span
                              v-if="room.isPrivate"
                              class="text-[10px] px-1.5 py-0.5 rounded-full bg-[#FF9500]/15 text-[#B25000] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A] shrink-0"
                            >
                              私密
                            </span>
                          </div>
                          <div class="text-[11px] font-mono-tabular text-[#8E8E93] flex items-center gap-2">
                            <span>#{{ room.code }}</span>
                            <span>·</span>
                            <span>{{ room.onlineCount ?? 0 }} 人在线</span>
                          </div>
                        </div>
                      </button>

                      <button
                        type="button"
                        @click.stop="handleRemoveRecentRoom(room.code)"
                        :disabled="isBusy"
                        title="移除此记录"
                        aria-label="移除此房间记录"
                        class="h-7 w-7 rounded-full inline-flex items-center justify-center text-[#8E8E93] hover:text-[#1C1C1E] hover:bg-black/[0.06] dark:hover:text-[#F5F5F7] dark:hover:bg-white/[0.1] shrink-0 transition-colors cursor-pointer"
                      >
                        <X class="w-3.5 h-3.5" />
                      </button>
                    </div>

                    <!-- 私密房原地输入密码 -->
                    <form
                      v-if="quickPrivateRoom?.code === room.code"
                      @submit.prevent="handleQuickPrivateSubmit(true)"
                      class="mt-2.5 pt-2.5 border-t border-[#FF9500]/15 dark:border-[#FF9F0A]/20 flex items-center gap-2"
                    >
                      <div class="relative flex-1">
                        <input
                          ref="quickPrivatePwdInputRef"
                          v-model="quickPrivatePassword"
                          @input="clearAllErrors"
                          :type="showQuickPrivatePassword ? 'text' : 'password'"
                          maxlength="20"
                          autocomplete="off"
                          placeholder="输入私密房间密码"
                          :disabled="isBusy"
                          class="w-full h-9 pl-3 pr-8 rounded-[10px] text-[13px] bg-white text-[#1C1C1E] placeholder:text-[#8E8E93] border border-black/[0.08] focus:outline-none focus:border-[#007AFF] dark:bg-[#1C1C1E] dark:text-[#F5F5F7] dark:border-white/[0.12] dark:focus:border-[#0A84FF]"
                        />
                        <button
                          type="button"
                          @click="showQuickPrivatePassword = !showQuickPrivatePassword"
                          class="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
                        >
                          <EyeOff v-if="showQuickPrivatePassword" class="w-3.5 h-3.5" />
                          <Eye v-else class="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <button
                        type="submit"
                        :disabled="isBusy || !quickPrivatePassword"
                        class="h-9 px-3 rounded-[10px] bg-[#007AFF] hover:bg-[#0066D6] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[12px] font-medium shrink-0 disabled:opacity-40 cursor-pointer"
                      >
                        进入
                      </button>
                    </form>
                  </div>
                </div>

                <p v-if="displayError" role="alert" class="mt-2.5 px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]">
                  {{ displayError }}
                </p>
              </div>
            </template>

            <!-- 标准昵称表单 -->
            <template v-else>
              <div class="flex flex-col items-center text-center mb-7">
                <div
                  class="h-[72px] w-[72px] rounded-full flex items-center justify-center text-2xl font-semibold select-none shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_10px_24px_-8px_rgba(0,0,0,0.22)] transition-all duration-200 mb-4"
                  :class="getAvatarBgClass(avatarColor)"
                  aria-hidden="true"
                >
                  {{ trimmedNickname ? getAvatarInitials(trimmedNickname) : '聊' }}
                </div>
                <h1 class="text-[22px] font-semibold tracking-[-0.025em] text-[#1C1C1E] dark:text-[#F5F5F7]">
                  设置你的用户名
                </h1>
                <p class="mt-1.5 text-[14px] text-[#636366] dark:text-[#98989F]">
                  设置昵称与头像色后，即可创建或加入房间
                </p>
              </div>

              <form @submit.prevent="handleConfirmIdentity" class="space-y-6" novalidate>
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
                      :class="nicknameCharLen > 20 ? 'text-[#FF3B30] dark:text-[#FF453A]' : 'text-[#8E8E93]'"
                    >
                      {{ nicknameCharLen }}/20
                    </span>
                  </div>

                  <input
                    id="nickname-input"
                    ref="nicknameInputRef"
                    v-model="nickname"
                    @input="clearAllErrors"
                    type="text"
                    maxlength="24"
                    autocomplete="nickname"
                    placeholder="例如：林浅、Alex"
                    class="w-full h-12 px-4 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 transition-all duration-150"
                  />

                  <p v-if="displayError" role="alert" class="mt-2 px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]">
                    {{ displayError }}
                  </p>
                </div>

                <div>
                  <span class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-3 px-1">
                    头像颜色
                  </span>
                  <div class="flex items-center justify-between gap-2 px-1" role="radiogroup" aria-label="选择头像颜色">
                    <button
                      v-for="item in AVATAR_COLORS"
                      :key="item.key"
                      type="button"
                      role="radio"
                      :aria-checked="avatarColor === item.key"
                      :aria-label="item.label"
                      :title="item.label"
                      @click="avatarColor = item.key"
                      class="h-10 w-10 rounded-full inline-flex items-center justify-center transition-all duration-150 hover:scale-105 active:scale-95 cursor-pointer shadow-[inset_0_1px_0_rgba(255,255,255,0.25)]"
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

                <button
                  type="submit"
                  :disabled="!canConfirmIdentity"
                  class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-40 disabled:shadow-none disabled:pointer-events-none cursor-pointer"
                >
                  <span>下一步：选择房间</span>
                  <ArrowRight class="w-4 h-4 shrink-0" />
                </button>

                <div v-if="savedProfile" class="pt-1 text-center">
                  <button
                    type="button"
                    @click="handleBackToQuickLogin"
                    class="inline-flex items-center gap-1.5 text-[13px] font-medium text-[#007AFF] hover:text-[#0066D6] dark:text-[#0A84FF] dark:hover:text-[#409CFF] transition-colors cursor-pointer"
                  >
                    <RotateCcw class="w-3.5 h-3.5 shrink-0" />
                    <span>使用上次记录的「{{ savedProfile.nickname }}」</span>
                  </button>
                </div>
              </form>

              <!-- 填写新昵称时也可直接点选最近进过的房间 -->
              <div
                v-if="recentRooms.length > 0"
                class="mt-6 pt-5 border-t border-black/[0.06] dark:border-white/[0.08]"
              >
                <div class="flex items-center justify-between mb-2.5 px-1">
                  <span class="inline-flex items-center gap-1.5 text-[12px] font-medium text-[#636366] dark:text-[#98989F]">
                    <Clock class="w-3.5 h-3.5" />
                    <span>最近进过的房间</span>
                  </span>
                  <span class="text-[11px] text-[#8E8E93]">输入昵称后点击快进</span>
                </div>

                <div class="space-y-2 max-h-[180px] overflow-y-auto chat-scroll pr-0.5">
                  <div
                    v-for="room in recentRooms"
                    :key="room.code"
                    class="rounded-[14px] transition-all"
                    :class="
                      quickPrivateRoom?.code === room.code
                        ? 'bg-[#FF9500]/[0.08] ring-1 ring-[#FF9500]/25 dark:bg-[#FF9F0A]/[0.1] dark:ring-[#FF9F0A]/30 p-3'
                        : 'bg-[#F2F2F7] hover:bg-[#E5E5EA] dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C]'
                    "
                  >
                    <div class="flex items-center gap-2.5" :class="quickPrivateRoom?.code === room.code ? '' : 'px-3 py-2.5'">
                      <button
                        type="button"
                        @click="handleSelectRecentRoom(room, false)"
                        :disabled="isBusy"
                        class="flex-1 flex items-center gap-2.5 min-w-0 text-left cursor-pointer disabled:opacity-50"
                      >
                        <span
                          class="h-8 w-8 rounded-[10px] flex items-center justify-center shrink-0"
                          :class="
                            room.isPrivate
                              ? 'bg-[#FF9500]/15 text-[#C96B00] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A]'
                              : 'bg-[#007AFF]/12 text-[#007AFF] dark:bg-[#0A84FF]/20 dark:text-[#0A84FF]'
                          "
                        >
                          <Lock v-if="room.isPrivate" class="w-3.5 h-3.5" />
                          <Globe v-else class="w-3.5 h-3.5" />
                        </span>
                        <div class="min-w-0 flex-1">
                          <div class="flex items-center gap-1.5 min-w-0">
                            <span class="text-[14px] font-medium text-[#1C1C1E] dark:text-[#F5F5F7] truncate">
                              {{ room.name }}
                            </span>
                            <span
                              v-if="room.isPrivate"
                              class="text-[10px] px-1.5 py-0.5 rounded-full bg-[#FF9500]/15 text-[#B25000] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A] shrink-0"
                            >
                              私密
                            </span>
                          </div>
                          <div class="text-[11px] font-mono-tabular text-[#8E8E93] flex items-center gap-2">
                            <span>#{{ room.code }}</span>
                            <span>·</span>
                            <span>{{ room.onlineCount ?? 0 }} 人在线</span>
                          </div>
                        </div>
                      </button>

                      <button
                        type="button"
                        @click.stop="handleRemoveRecentRoom(room.code)"
                        :disabled="isBusy"
                        title="移除此记录"
                        aria-label="移除此房间记录"
                        class="h-7 w-7 rounded-full inline-flex items-center justify-center text-[#8E8E93] hover:text-[#1C1C1E] hover:bg-black/[0.06] dark:hover:text-[#F5F5F7] dark:hover:bg-white/[0.1] shrink-0 transition-colors cursor-pointer"
                      >
                        <X class="w-3.5 h-3.5" />
                      </button>
                    </div>

                    <form
                      v-if="quickPrivateRoom?.code === room.code"
                      @submit.prevent="handleQuickPrivateSubmit(false)"
                      class="mt-2.5 pt-2.5 border-t border-[#FF9500]/15 dark:border-[#FF9F0A]/20 flex items-center gap-2"
                    >
                      <div class="relative flex-1">
                        <input
                          ref="quickPrivatePwdInputRef"
                          v-model="quickPrivatePassword"
                          @input="clearAllErrors"
                          :type="showQuickPrivatePassword ? 'text' : 'password'"
                          maxlength="20"
                          autocomplete="off"
                          placeholder="输入私密房间密码"
                          :disabled="isBusy"
                          class="w-full h-9 pl-3 pr-8 rounded-[10px] text-[13px] bg-white text-[#1C1C1E] placeholder:text-[#8E8E93] border border-black/[0.08] focus:outline-none focus:border-[#007AFF] dark:bg-[#1C1C1E] dark:text-[#F5F5F7] dark:border-white/[0.12] dark:focus:border-[#0A84FF]"
                        />
                        <button
                          type="button"
                          @click="showQuickPrivatePassword = !showQuickPrivatePassword"
                          class="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
                        >
                          <EyeOff v-if="showQuickPrivatePassword" class="w-3.5 h-3.5" />
                          <Eye v-else class="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <button
                        type="submit"
                        :disabled="isBusy || !quickPrivatePassword"
                        class="h-9 px-3 rounded-[10px] bg-[#007AFF] hover:bg-[#0066D6] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[12px] font-medium shrink-0 disabled:opacity-40 cursor-pointer"
                      >
                        进入
                      </button>
                    </form>
                  </div>
                </div>
              </div>
            </template>
          </template>

          <!-- ========================================================= -->
          <!-- Step 2：选择「加入房间」或「创建房间」                       -->
          <!-- ========================================================= -->
          <template v-else>
            <!-- 当前已确认的身份栏 -->
            <div
              class="mb-6 px-3.5 py-2.5 rounded-[16px] bg-[#F2F2F7] dark:bg-[#2C2C2E] flex items-center justify-between gap-3"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <div
                  class="h-9 w-9 rounded-full flex items-center justify-center text-[13px] font-semibold shrink-0 shadow-[inset_0_1px_0_rgba(255,255,255,0.25)]"
                  :class="getAvatarBgClass(avatarColor)"
                >
                  {{ getAvatarInitials(trimmedNickname) }}
                </div>
                <div class="min-w-0">
                  <p class="text-[11px] text-[#8E8E93]">当前身份</p>
                  <p class="text-[14px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7] truncate">
                    {{ trimmedNickname }}
                  </p>
                </div>
              </div>
              <button
                type="button"
                @click="handleEditIdentity"
                :disabled="isBusy"
                class="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-full text-[12px] font-medium text-[#007AFF] hover:bg-[#007AFF]/10 dark:text-[#0A84FF] dark:hover:bg-[#0A84FF]/15 transition-colors shrink-0 cursor-pointer"
              >
                <ArrowLeft class="w-3.5 h-3.5" />
                <span>改名</span>
              </button>
            </div>

            <!-- iOS 分段选项卡：加入房间 / 创建房间 -->
            <div
              class="mb-6 p-1 rounded-[14px] bg-[#F2F2F7] dark:bg-[#2C2C2E] grid grid-cols-2 gap-1"
              role="tablist"
            >
              <button
                type="button"
                role="tab"
                :aria-selected="actionTab === 'join'"
                @click="actionTab = 'join'"
                class="h-9 rounded-[11px] text-[14px] font-medium inline-flex items-center justify-center gap-1.5 transition-all cursor-pointer"
                :class="
                  actionTab === 'join'
                    ? 'bg-white text-[#1C1C1E] shadow-xs dark:bg-[#3A3A3C] dark:text-[#F5F5F7]'
                    : 'text-[#636366] hover:text-[#1C1C1E] dark:text-[#98989F] dark:hover:text-[#F5F5F7]'
                "
              >
                <DoorOpen class="w-4 h-4" />
                <span>加入房间</span>
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="actionTab === 'create'"
                @click="actionTab = 'create'"
                class="h-9 rounded-[11px] text-[14px] font-medium inline-flex items-center justify-center gap-1.5 transition-all cursor-pointer"
                :class="
                  actionTab === 'create'
                    ? 'bg-white text-[#1C1C1E] shadow-xs dark:bg-[#3A3A3C] dark:text-[#F5F5F7]'
                    : 'text-[#636366] hover:text-[#1C1C1E] dark:text-[#98989F] dark:hover:text-[#F5F5F7]'
                "
              >
                <Plus class="w-4 h-4" />
                <span>创建房间</span>
              </button>
            </div>

            <!-- Tab 1: 加入房间 -->
            <form
              v-if="actionTab === 'join'"
              @submit.prevent="handleJoinRoomSubmit"
              class="space-y-5"
              novalidate
            >
              <div>
                <label
                  for="join-room-code"
                  class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-2 px-1"
                >
                  房间号
                </label>
                <div class="relative">
                  <Hash
                    class="w-4 h-4 text-[#8E8E93] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"
                  />
                  <input
                    id="join-room-code"
                    ref="joinCodeInputRef"
                    v-model="joinRoomCode"
                    type="text"
                    inputmode="numeric"
                    maxlength="16"
                    autocomplete="off"
                    placeholder="输入 6 位房间号，如 482910"
                    :disabled="isBusy"
                    class="w-full h-12 pl-10 pr-4 rounded-[14px] font-mono-tabular text-[16px] tracking-wider bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] placeholder:tracking-normal placeholder:font-sans placeholder:text-[14px] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                  />
                </div>
                <p class="mt-2 px-1 text-[12px] text-[#8E8E93]">
                  公开房间将直接进入，私密房间会提示输入密码
                </p>
              </div>

              <!-- 查到私密房后展开密码输入框 -->
              <div
                v-if="lookedUpRoom && lookedUpRoom.isPrivate"
                class="space-y-3 p-4 rounded-[16px] bg-[#FF9500]/[0.08] ring-1 ring-[#FF9500]/20 dark:bg-[#FF9F0A]/[0.1] dark:ring-[#FF9F0A]/25"
              >
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <Lock class="w-4 h-4 text-[#C96B00] dark:text-[#FF9F0A] shrink-0" />
                    <span class="text-[14px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7] truncate">
                      {{ lookedUpRoom.name }}
                    </span>
                  </div>
                  <span class="text-[12px] font-mono-tabular text-[#636366] dark:text-[#98989F] shrink-0">
                    {{ lookedUpRoom.onlineCount ?? 0 }} 人在线
                  </span>
                </div>

                <div>
                  <label
                    for="join-room-password"
                    class="block text-[12px] font-medium text-[#636366] dark:text-[#98989F] mb-1.5"
                  >
                    该房间为私密房间，请输入房间密码
                  </label>
                  <div class="relative">
                    <input
                      id="join-room-password"
                      ref="joinPwdInputRef"
                      v-model="joinPassword"
                      @input="clearAllErrors"
                      :type="showJoinPassword ? 'text' : 'password'"
                      maxlength="20"
                      autocomplete="off"
                      placeholder="输入房间密码"
                      :disabled="isBusy"
                      class="w-full h-11 pl-3.5 pr-10 rounded-[12px] text-[15px] bg-white text-[#1C1C1E] placeholder:text-[#8E8E93] border border-black/[0.08] focus:outline-none focus:border-[#007AFF] focus:ring-3 focus:ring-[#007AFF]/15 dark:bg-[#1C1C1E] dark:text-[#F5F5F7] dark:border-white/[0.12] dark:focus:border-[#0A84FF] transition-all"
                    />
                    <button
                      type="button"
                      @click="showJoinPassword = !showJoinPassword"
                      class="absolute right-2.5 top-1/2 -translate-y-1/2 p-1.5 text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
                      :aria-label="showJoinPassword ? '隐藏密码' : '显示密码'"
                    >
                      <EyeOff v-if="showJoinPassword" class="w-4 h-4" />
                      <Eye v-else class="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>

              <p v-if="displayError" role="alert" class="px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]">
                {{ displayError }}
              </p>

              <button
                type="submit"
                :disabled="!joinRoomCode.trim() || isBusy"
                class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-40 disabled:shadow-none disabled:pointer-events-none cursor-pointer"
              >
                <Loader2 v-if="isBusy" class="w-4 h-4 animate-spin shrink-0" />
                <span>
                  {{
                    isLookingUp
                      ? '正在查找房间...'
                      : status === 'connecting'
                        ? '正在进入房间...'
                        : lookedUpRoom?.isPrivate
                          ? '验证密码并进入'
                          : '进入房间'
                  }}
                </span>
                <ArrowRight v-if="!isBusy" class="w-4 h-4 shrink-0" />
              </button>

              <!-- Step 2 加入房间下方也展示最近进过的房间 -->
              <div
                v-if="recentRooms.length > 0"
                class="pt-4 border-t border-black/[0.06] dark:border-white/[0.08]"
              >
                <div class="flex items-center justify-between mb-2.5 px-1">
                  <span class="inline-flex items-center gap-1.5 text-[12px] font-medium text-[#636366] dark:text-[#98989F]">
                    <Clock class="w-3.5 h-3.5" />
                    <span>最近进过的房间</span>
                  </span>
                  <span class="text-[11px] text-[#8E8E93]">点击快速进入</span>
                </div>

                <div class="space-y-2 max-h-[190px] overflow-y-auto chat-scroll pr-0.5">
                  <div
                    v-for="room in recentRooms"
                    :key="room.code"
                    class="flex items-center gap-2.5 px-3 py-2.5 rounded-[14px] transition-colors"
                    :class="
                      lookedUpRoom?.code === room.code
                        ? 'bg-[#FF9500]/[0.08] ring-1 ring-[#FF9500]/25 dark:bg-[#FF9F0A]/[0.1] dark:ring-[#FF9F0A]/30'
                        : 'bg-[#F2F2F7] hover:bg-[#E5E5EA] dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C]'
                    "
                  >
                    <button
                      type="button"
                      @click="handleSelectRecentRoom(room, false)"
                      :disabled="isBusy"
                      class="flex-1 flex items-center gap-2.5 min-w-0 text-left cursor-pointer disabled:opacity-50"
                    >
                      <span
                        class="h-8 w-8 rounded-[10px] flex items-center justify-center shrink-0"
                        :class="
                          room.isPrivate
                            ? 'bg-[#FF9500]/15 text-[#C96B00] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A]'
                            : 'bg-[#007AFF]/12 text-[#007AFF] dark:bg-[#0A84FF]/20 dark:text-[#0A84FF]'
                        "
                      >
                        <Lock v-if="room.isPrivate" class="w-3.5 h-3.5" />
                        <Globe v-else class="w-3.5 h-3.5" />
                      </span>
                      <div class="min-w-0 flex-1">
                        <div class="flex items-center gap-1.5 min-w-0">
                          <span class="text-[14px] font-medium text-[#1C1C1E] dark:text-[#F5F5F7] truncate">
                            {{ room.name }}
                          </span>
                          <span
                            v-if="room.isPrivate"
                            class="text-[10px] px-1.5 py-0.5 rounded-full bg-[#FF9500]/15 text-[#B25000] dark:bg-[#FF9F0A]/20 dark:text-[#FF9F0A] shrink-0"
                          >
                            私密
                          </span>
                        </div>
                        <div class="text-[11px] font-mono-tabular text-[#8E8E93] flex items-center gap-2">
                          <span>#{{ room.code }}</span>
                          <span>·</span>
                          <span>{{ room.onlineCount ?? 0 }} 人在线</span>
                        </div>
                      </div>
                    </button>

                    <button
                      type="button"
                      @click.stop="handleRemoveRecentRoom(room.code)"
                      :disabled="isBusy"
                      title="移除此记录"
                      aria-label="移除此房间记录"
                      class="h-7 w-7 rounded-full inline-flex items-center justify-center text-[#8E8E93] hover:text-[#1C1C1E] hover:bg-black/[0.06] dark:hover:text-[#F5F5F7] dark:hover:bg-white/[0.1] shrink-0 transition-colors cursor-pointer"
                    >
                      <X class="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            </form>

            <!-- Tab 2: 创建房间 -->
            <form v-else @submit.prevent="handleCreateRoomSubmit" class="space-y-5" novalidate>
              <div>
                <div class="flex items-center justify-between mb-2 px-1">
                  <label
                    for="create-room-name"
                    class="text-[13px] font-medium text-[#636366] dark:text-[#98989F]"
                  >
                    房间名称
                  </label>
                  <span
                    class="text-xs font-mono-tabular"
                    :class="createNameLen > 24 ? 'text-[#FF3B30] dark:text-[#FF453A]' : 'text-[#8E8E93]'"
                  >
                    {{ createNameLen }}/24
                  </span>
                </div>
                <input
                  id="create-room-name"
                  ref="createNameInputRef"
                  v-model="createRoomName"
                  @input="clearAllErrors"
                  type="text"
                  maxlength="28"
                  placeholder="例如：周末闲聊、项目讨论"
                  :disabled="isBusy"
                  class="w-full h-12 px-4 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                />
              </div>

              <!-- 房间可见性选择 -->
              <div>
                <span class="block text-[13px] font-medium text-[#636366] dark:text-[#98989F] mb-2 px-1">
                  房间类型
                </span>
                <div class="grid grid-cols-2 gap-2.5">
                  <button
                    type="button"
                    @click="createIsPrivate = false; clearAllErrors()"
                    :disabled="isBusy"
                    class="p-3 rounded-[14px] text-left transition-all cursor-pointer ring-1"
                    :class="
                      !createIsPrivate
                        ? 'bg-[#007AFF]/[0.08] ring-[#007AFF] dark:bg-[#0A84FF]/15 dark:ring-[#0A84FF]'
                        : 'bg-[#F2F2F7] ring-transparent hover:bg-[#E5E5EA] dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C]'
                    "
                  >
                    <div class="flex items-center gap-1.5 text-[14px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7]">
                      <Globe class="w-4 h-4 text-[#007AFF] dark:text-[#0A84FF]" />
                      <span>公开房间</span>
                    </div>
                    <p class="mt-1 text-[11px] text-[#636366] dark:text-[#98989F]">
                      凭房间号或链接直接进
                    </p>
                  </button>

                  <button
                    type="button"
                    @click="createIsPrivate = true; clearAllErrors()"
                    :disabled="isBusy"
                    class="p-3 rounded-[14px] text-left transition-all cursor-pointer ring-1"
                    :class="
                      createIsPrivate
                        ? 'bg-[#FF9500]/[0.1] ring-[#FF9500] dark:bg-[#FF9F0A]/15 dark:ring-[#FF9F0A]'
                        : 'bg-[#F2F2F7] ring-transparent hover:bg-[#E5E5EA] dark:bg-[#2C2C2E] dark:hover:bg-[#3A3A3C]'
                    "
                  >
                    <div class="flex items-center gap-1.5 text-[14px] font-semibold text-[#1C1C1E] dark:text-[#F5F5F7]">
                      <Lock class="w-4 h-4 text-[#C96B00] dark:text-[#FF9F0A]" />
                      <span>私密房间</span>
                    </div>
                    <p class="mt-1 text-[11px] text-[#636366] dark:text-[#98989F]">
                      需输入房间密码才能进
                    </p>
                  </button>
                </div>
              </div>

              <!-- 私密房间密码 -->
              <div v-if="createIsPrivate">
                <div class="flex items-center justify-between mb-2 px-1">
                  <label
                    for="create-room-password"
                    class="text-[13px] font-medium text-[#636366] dark:text-[#98989F]"
                  >
                    设置房间密码
                  </label>
                  <span class="text-xs font-mono-tabular text-[#8E8E93]">
                    4-20 位
                  </span>
                </div>
                <div class="relative">
                  <input
                    id="create-room-password"
                    v-model="createPassword"
                    @input="clearAllErrors"
                    :type="showCreatePassword ? 'text' : 'password'"
                    maxlength="20"
                    autocomplete="new-password"
                    placeholder="请输入 4 到 20 位密码"
                    :disabled="isBusy"
                    class="w-full h-12 pl-4 pr-11 rounded-[14px] text-[15px] bg-[#F2F2F7] text-[#1C1C1E] placeholder:text-[#8E8E93] border border-transparent focus:bg-white focus:outline-none focus:border-[#007AFF] focus:ring-4 focus:ring-[#007AFF]/15 dark:bg-[#2C2C2E] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:bg-[#2C2C2E] dark:focus:border-[#0A84FF] dark:focus:ring-[#0A84FF]/20 disabled:opacity-60 transition-all duration-150"
                  />
                  <button
                    type="button"
                    @click="showCreatePassword = !showCreatePassword"
                    class="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
                    :aria-label="showCreatePassword ? '隐藏密码' : '显示密码'"
                  >
                    <EyeOff v-if="showCreatePassword" class="w-4 h-4" />
                    <Eye v-else class="w-4 h-4" />
                  </button>
                </div>
              </div>

              <p v-if="displayError" role="alert" class="px-1 text-[13px] text-[#FF3B30] dark:text-[#FF453A]">
                {{ displayError }}
              </p>

              <button
                type="submit"
                :disabled="!canSubmitCreate"
                class="w-full h-12 px-5 rounded-[14px] bg-[#007AFF] hover:bg-[#0066D6] active:scale-[0.985] dark:bg-[#0A84FF] dark:hover:bg-[#3395FF] text-white text-[15px] font-medium inline-flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(0,122,255,0.26)] dark:shadow-[0_4px_16px_rgba(10,132,255,0.3)] transition-all duration-150 disabled:opacity-40 disabled:shadow-none disabled:pointer-events-none cursor-pointer"
              >
                <Loader2 v-if="isBusy" class="w-4 h-4 animate-spin shrink-0" />
                <span>
                  {{
                    isCreating
                      ? '正在创建房间...'
                      : status === 'connecting'
                        ? '正在进入房间...'
                        : '创建并进入房间'
                  }}
                </span>
                <ArrowRight v-if="!isBusy" class="w-4 h-4 shrink-0" />
              </button>
            </form>
          </template>
        </div>
      </div>
    </main>
  </div>
</template>
