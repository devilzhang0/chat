import {ref} from 'vue';
import type {
  AvatarColorKey,
  ChatMessage,
  ConnectionStatus,
  OnlineUser,
  ServerEvent,
} from '@/types/chat';

const VALID_AVATAR_COLORS: AvatarColorKey[] = [
  'indigo',
  'slate',
  'emerald',
  'amber',
  'rose',
  'cyan',
];

export interface SavedProfile {
  nickname: string;
  avatarColor: AvatarColorKey;
}

/** 从后端读取当前游客身份（基于 HttpOnly 的 guest_id cookie） */
export async function fetchMe(): Promise<SavedProfile | null> {
  try {
    const res = await fetch('/api/auth/me', {credentials: 'include'});
    if (!res.ok) return null;
    const body = await res.json();
    const data = body?.data;
    if (!data || data.found !== true) return null;
    const nickname = typeof data.nickname === 'string' ? data.nickname.trim() : '';
    const charLen = Array.from(nickname).length;
    if (charLen < 2 || charLen > 20) return null;
    const avatarColor: AvatarColorKey =
      data.avatarColor && VALID_AVATAR_COLORS.includes(data.avatarColor)
        ? data.avatarColor
        : 'indigo';
    return {nickname, avatarColor};
  } catch {
    return null;
  }
}

/** 清除后端 guest_id cookie（忘记当前设备上的身份） */
export async function clearIdentity(): Promise<void> {
  try {
    await fetch('/api/auth/clear', {method: 'POST', credentials: 'include'});
  } catch {
    // ignore
  }
}

const currentUser = ref<OnlineUser | null>(null);
const messages = ref<ChatMessage[]>([]);
const onlineUsers = ref<OnlineUser[]>([]);
const status = ref<ConnectionStatus>('idle');
const isLoadingHistory = ref(false);
const errorMessage = ref<string | null>(null);

let socket: WebSocket | null = null;
let shouldAutoReconnect = false;
let reconnectAttempt = 0;
let reconnectTimer: number | null = null;
let heartbeatTimer: number | null = null;
let pendingCredentials: SavedProfile | null = null;
let errorClearTimer: number | null = null;

function setTransientError(msg: string): void {
  errorMessage.value = msg;
  if (errorClearTimer !== null) {
    window.clearTimeout(errorClearTimer);
  }
  errorClearTimer = window.setTimeout(() => {
    errorMessage.value = null;
    errorClearTimer = null;
  }, 4000);
}

function clearTimers(): void {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function startHeartbeat(): void {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer);
  }
  heartbeatTimer = window.setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({type: 'ping'}));
    }
  }, 25000);
}

function getWebSocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

function upsertMessageIdempotent(incoming: ChatMessage): void {
  if (messages.value.some((m) => m.id === incoming.id)) {
    return;
  }
  messages.value.push(incoming);
}

function mergeHistoryIdempotent(serverMessages: ChatMessage[]): void {
  const seen = new Set<string>();
  const deduped: ChatMessage[] = [];
  for (const msg of serverMessages) {
    if (!seen.has(msg.id)) {
      seen.add(msg.id);
      deduped.push(msg);
    }
  }
  messages.value = deduped;
}

function scheduleReconnect(): void {
  if (!shouldAutoReconnect || !pendingCredentials) return;
  if (reconnectTimer !== null) return;

  status.value = 'reconnecting';
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempt), 10000);
  reconnectAttempt += 1;

  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    if (!shouldAutoReconnect || !pendingCredentials) return;
    openSocket(pendingCredentials, false).catch(() => {
      scheduleReconnect();
    });
  }, delay);
}

function openSocket(profile: SavedProfile, isInitialLogin: boolean): Promise<boolean> {
  return new Promise((resolve) => {
    if (socket) {
      socket.onopen = null;
      socket.onmessage = null;
      socket.onclose = null;
      socket.onerror = null;
      try {
        socket.close();
      } catch {
        // ignore
      }
      socket = null;
    }

    let settled = false;
    const finish = (ok: boolean) => {
      if (!settled) {
        settled = true;
        resolve(ok);
      }
    };

    if (isInitialLogin) {
      status.value = 'connecting';
      isLoadingHistory.value = true;
      errorMessage.value = null;
    }

    let ws: WebSocket;
    try {
      ws = new WebSocket(getWebSocketUrl());
    } catch {
      status.value = 'disconnected';
      isLoadingHistory.value = false;
      setTransientError('无法建立实时连接，请检查网络后重试');
      finish(false);
      return;
    }

    socket = ws;

    const connectTimeout = window.setTimeout(() => {
      if (!settled) {
        try {
          ws.close();
        } catch {
          // ignore
        }
        status.value = isInitialLogin ? 'idle' : 'disconnected';
        isLoadingHistory.value = false;
        setTransientError('连接超时，请稍后重试');
        finish(false);
      }
    }, 8000);

    ws.onopen = () => {
      ws.send(
        JSON.stringify({
          type: 'join',
          nickname: profile.nickname,
          avatarColor: profile.avatarColor,
        })
      );
    };

    ws.onmessage = (event) => {
      let payload: ServerEvent;
      try {
        payload = JSON.parse(String(event.data)) as ServerEvent;
      } catch {
        return;
      }

      if (payload.type === 'sync:init') {
        window.clearTimeout(connectTimeout);
        currentUser.value = payload.self;
        onlineUsers.value = payload.onlineUsers;
        mergeHistoryIdempotent(payload.messages);
        status.value = 'connected';
        isLoadingHistory.value = false;
        reconnectAttempt = 0;
        errorMessage.value = null;
        startHeartbeat();
        finish(true);
        return;
      }

      if (payload.type === 'message:new') {
        upsertMessageIdempotent(payload.message);
        return;
      }

      if (payload.type === 'presence:update') {
        onlineUsers.value = payload.onlineUsers;
        return;
      }

      if (payload.type === 'error') {
        window.clearTimeout(connectTimeout);
        setTransientError(payload.message);
        if (!settled) {
          isLoadingHistory.value = false;
          status.value = 'idle';
          finish(false);
        }
      }
    };

    ws.onerror = () => {
      window.clearTimeout(connectTimeout);
      if (!settled) {
        isLoadingHistory.value = false;
        if (isInitialLogin) {
          status.value = 'idle';
          setTransientError('连接聊天服务器失败，请稍后重试');
        }
        finish(false);
      }
    };

    ws.onclose = () => {
      window.clearTimeout(connectTimeout);
      if (heartbeatTimer !== null) {
        window.clearInterval(heartbeatTimer);
        heartbeatTimer = null;
      }
      if (!settled) {
        isLoadingHistory.value = false;
        finish(false);
      }
      if (shouldAutoReconnect && currentUser.value) {
        scheduleReconnect();
      } else if (!currentUser.value) {
        status.value = 'idle';
      } else {
        status.value = 'disconnected';
      }
    };
  });
}

export function useChatSocket() {
  async function login(nickname: string, avatarColor: AvatarColorKey): Promise<boolean> {
    const trimmed = nickname.trim();
    const profile: SavedProfile = {nickname: trimmed, avatarColor};
    pendingCredentials = profile;
    shouldAutoReconnect = true;
    reconnectAttempt = 0;

    const ok = await openSocket(profile, true);
    if (!ok) {
      shouldAutoReconnect = false;
    }
    return ok;
  }

  function sendMessage(content: string): boolean {
    const trimmed = content.trim();
    if (!trimmed) return false;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      setTransientError('当前未连接到服务器，正在尝试重新连接');
      return false;
    }
    const clientTempId =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

    socket.send(
      JSON.stringify({
        type: 'message:send',
        content: trimmed,
        clientTempId,
      })
    );
    return true;
  }

  function manualReconnect(): void {
    if (!pendingCredentials) return;
    clearTimers();
    shouldAutoReconnect = true;
    reconnectAttempt = 0;
    status.value = 'reconnecting';
    openSocket(pendingCredentials, false).catch(() => {
      scheduleReconnect();
    });
  }

  function logout(): void {
    shouldAutoReconnect = false;
    pendingCredentials = null;
    clearTimers();

    if (socket) {
      if (socket.readyState === WebSocket.OPEN) {
        try {
          socket.send(JSON.stringify({type: 'leave'}));
        } catch {
          // ignore
        }
      }
      socket.onopen = null;
      socket.onmessage = null;
      socket.onclose = null;
      socket.onerror = null;
      try {
        socket.close();
      } catch {
        // ignore
      }
      socket = null;
    }

    currentUser.value = null;
    onlineUsers.value = [];
    messages.value = [];
    status.value = 'idle';
    isLoadingHistory.value = false;
    errorMessage.value = null;
  }

  function clearError(): void {
    errorMessage.value = null;
  }

  return {
    currentUser,
    messages,
    onlineUsers,
    status,
    isLoadingHistory,
    errorMessage,
    login,
    sendMessage,
    manualReconnect,
    logout,
    clearError,
  };
}
