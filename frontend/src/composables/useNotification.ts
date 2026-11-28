import {onBeforeUnmount, onMounted, ref, watch, type Ref} from 'vue';

const SOUND_STORAGE_KEY = 'mychat_sound_enabled';
const DEFAULT_TITLE = 'MyChat';
const TONE_COOLDOWN_MS = 600;

function readInitialSoundEnabled(): boolean {
  try {
    const saved = window.localStorage.getItem(SOUND_STORAGE_KEY);
    if (saved === 'false') return false;
    if (saved === 'true') return true;
  } catch {
    // ignore storage errors
  }
  return true;
}

const soundEnabled = ref<boolean>(readInitialSoundEnabled());

let sharedAudioCtx: AudioContext | null = null;
let lastTonePlayedAt = 0;
let audioUnlockBound = false;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (sharedAudioCtx) return sharedAudioCtx;
  const Ctor =
    window.AudioContext ||
    (window as unknown as {webkitAudioContext?: typeof AudioContext}).webkitAudioContext;
  if (!Ctor) return null;
  try {
    sharedAudioCtx = new Ctor();
    return sharedAudioCtx;
  } catch {
    return null;
  }
}

function unlockAudioContext(): void {
  const ctx = getAudioContext();
  if (ctx && ctx.state === 'suspended') {
    ctx.resume().catch(() => {
      // ignore autoplay policy errors until next gesture
    });
  }
}

function bindAudioUnlockOnce(): void {
  if (audioUnlockBound || typeof window === 'undefined') return;
  audioUnlockBound = true;
  const unlock = () => {
    unlockAudioContext();
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
  window.addEventListener('pointerdown', unlock, {passive: true});
  window.addEventListener('keydown', unlock, {passive: true});
}

/** 使用 Web Audio API 合成柔和的双音提示音（零音频文件依赖） */
function playMessageTone(force = false): void {
  if (!force && !soundEnabled.value) return;

  const nowMs = Date.now();
  if (!force && nowMs - lastTonePlayedAt < TONE_COOLDOWN_MS) {
    return;
  }

  const ctx = getAudioContext();
  if (!ctx) return;

  const trigger = () => {
    try {
      const now = ctx.currentTime;
      const masterGain = ctx.createGain();
      masterGain.gain.setValueAtTime(0.0001, now);
      masterGain.gain.exponentialRampToValueAtTime(0.065, now + 0.016);
      masterGain.gain.exponentialRampToValueAtTime(0.045, now + 0.095);
      masterGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.24);
      masterGain.connect(ctx.destination);

      // 第一音：柔和 G5 (783.99 Hz)
      const osc1 = ctx.createOscillator();
      osc1.type = 'sine';
      osc1.frequency.setValueAtTime(783.99, now);
      osc1.connect(masterGain);
      osc1.start(now);
      osc1.stop(now + 0.095);

      // 第二音：轻快上扬 C6 (1046.5 Hz)
      const osc2 = ctx.createOscillator();
      osc2.type = 'sine';
      osc2.frequency.setValueAtTime(1046.5, now + 0.085);
      osc2.connect(masterGain);
      osc2.start(now + 0.085);
      osc2.stop(now + 0.25);

      lastTonePlayedAt = Date.now();
    } catch {
      // ignore Web Audio synthesis errors
    }
  };

  if (ctx.state === 'suspended') {
    ctx.resume().then(trigger).catch(() => {
      // browser blocked autoplay before user gesture
    });
  } else {
    trigger();
  }
}

/**
 * 聊天室后台标签页未读标题提醒 + Web Audio 轻提示音
 */
export function useNotification(roomName: Ref<string | undefined>) {
  const backgroundUnread = ref(0);
  const hasMentionUnread = ref(false);

  function syncDocumentTitle(): void {
    const name = roomName.value?.trim();
    const baseTitle = name ? `${name} - ${DEFAULT_TITLE}` : DEFAULT_TITLE;
    if (backgroundUnread.value > 0) {
      const countText = backgroundUnread.value > 99 ? '99+' : String(backgroundUnread.value);
      const mentionPrefix = hasMentionUnread.value ? '[有人@你] ' : '';
      document.title = `${mentionPrefix}(${countText}) ${baseTitle}`;
    } else {
      document.title = baseTitle;
    }
  }

  function clearBackgroundUnread(): void {
    if (backgroundUnread.value !== 0) {
      backgroundUnread.value = 0;
    }
    if (hasMentionUnread.value) {
      hasMentionUnread.value = false;
    }
    syncDocumentTitle();
  }

  function handleVisibilityOrFocus(): void {
    if (!document.hidden) {
      clearBackgroundUnread();
    }
  }

  function notifyIncomingMessage(options?: {mentioned?: boolean}): void {
    const mentioned = Boolean(options?.mentioned);
    if (document.hidden) {
      backgroundUnread.value += 1;
      if (mentioned) {
        hasMentionUnread.value = true;
      }
      syncDocumentTitle();
    }
    if (soundEnabled.value) {
      playMessageTone(mentioned);
    }
  }

  function toggleSound(): void {
    const next = !soundEnabled.value;
    soundEnabled.value = next;
    try {
      window.localStorage.setItem(SOUND_STORAGE_KEY, String(next));
    } catch {
      // ignore storage errors
    }
    if (next) {
      // 开启提示音时轻播一次，既做反馈也顺便解锁 AudioContext
      playMessageTone(true);
    }
  }

  watch(roomName, () => {
    syncDocumentTitle();
  });

  onMounted(() => {
    bindAudioUnlockOnce();
    syncDocumentTitle();
    document.addEventListener('visibilitychange', handleVisibilityOrFocus);
    window.addEventListener('focus', handleVisibilityOrFocus);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', handleVisibilityOrFocus);
    window.removeEventListener('focus', handleVisibilityOrFocus);
    document.title = DEFAULT_TITLE;
  });

  return {
    soundEnabled,
    backgroundUnread,
    toggleSound,
    notifyIncomingMessage,
    clearBackgroundUnread,
  };
}
