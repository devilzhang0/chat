import {ref} from 'vue';
import type {
  AvatarColorKey,
  ChatMessage,
  ConnectionStatus,
  CreateRoomPayload,
  JoinRoomPayload,
  OnlineUser,
  RoomInfo,
  RoomLookupResult,
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

export interface JoinResult {
  ok: boolean;
  code?: string;
  message?: string;
}

const RECENT_ROOMS_COOKIE = 'recent_rooms';
const RECENT_ROOMS_MAX_AGE = 60 * 60 * 24 * 30; // 30 天
const MAX_RECENT_ROOMS = 10;

function readRecentRoomCodesFromCookie(): string[] {
  try {
    const entries = document.cookie ? document.cookie.split('; ') : [];
    for (const entry of entries) {
      const idx = entry.indexOf('=');
      if (idx === -1) continue;
      const name = entry.slice(0, idx);
      if (name === RECENT_ROOMS_COOKIE) {
        const raw = decodeURIComponent(entry.slice(idx + 1));
        const seen = new Set<string>();
        const codes: string[] = [];
        for (const part of raw.split(/[-,]/)) {
          const code = part.trim();
          if (code && !seen.has(code)) {
            seen.add(code);
            codes.push(code);
          }
        }
        return codes.slice(0, MAX_RECENT_ROOMS);
      }
    }
  } catch {
    // ignore
  }
  return [];
}

function writeRecentRoomCodesToCookie(codes: string[]): void {
  try {
    const cleaned = codes.map((c) => c.trim()).filter(Boolean).slice(0, MAX_RECENT_ROOMS);
    if (cleaned.length === 0) {
      document.cookie = `${RECENT_ROOMS_COOKIE}=; path=/; max-age=0; SameSite=Lax`;
      return;
    }
    const value = encodeURIComponent(cleaned.join('-'));
    document.cookie = `${RECENT_ROOMS_COOKIE}=${value}; path=/; max-age=${RECENT_ROOMS_MAX_AGE}; SameSite=Lax`;
  } catch {
    // ignore
  }
}

/** 将成功进入过的房间号置顶写入 cookie */
export function recordRecentRoomCookie(code: string): void {
  const trimmed = code.trim();
  if (!trimmed) return;
  const existing = readRecentRoomCodesFromCookie();
  const next = [trimmed, ...existing.filter((c) => c !== trimmed)].slice(0, MAX_RECENT_ROOMS);
  writeRecentRoomCodesToCookie(next);
}

/** 从 cookie 中移除单个房间记录 */
export function removeRecentRoomCookie(code: string): void {
  const trimmed = code.trim();
  const next = readRecentRoomCodesFromCookie().filter((c) => c !== trimmed);
  writeRecentRoomCodesToCookie(next);
}

/** 清空 cookie 中记录的所有进过的房间 */
export function clearRecentRoomsCookie(): void {
  writeRecentRoomCodesToCookie([]);
}

/** 根据 cookie 中的房间号向后端批量查询实时房间信息 */
export async function fetchRecentRoomsApi(): Promise<RoomInfo[]> {
  try {
    const res = await fetch('/api/rooms/recent', {credentials: 'include'});
    if (!res.ok) return [];
    const body = await res.json().catch(() => null);
    if (!body?.success || !Array.isArray(body.data)) return [];
    return body.data
      .filter((item: Record<string, unknown>) => item && typeof item.code === 'string')
      .map((item: Record<string, unknown>) => ({
        code: String(item.code),
        name: String(item.name || `房间 ${item.code}`),
        isPrivate: Boolean(item.isPrivate),
        onlineCount: typeof item.onlineCount === 'number' ? item.onlineCount : 0,
      }));
  } catch {
    return [];
  }
}

/** 从 URL 读取分享链接携带的房间号（/?room=xxxxxx） */
export function getRoomCodeFromUrl(): string | null {
  try {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('room')?.trim();
    return code || null;
  } catch {
    return null;
  }
}

/** 同步或清除地址栏中的 ?room= 参数 */
export function setRoomCodeInUrl(code: string | null): void {
  try {
    const url = new URL(window.location.href);
    if (code) {
      url.searchParams.set('room', code);
    } else {
      url.searchParams.delete('room');
    }
    window.history.replaceState({}, '', url.pathname + url.search + url.hash);
  } catch {
    // ignore
  }
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

/** 创建新聊天室（公开/私密） */
export async function createRoomApi(
  payload: CreateRoomPayload
): Promise<{ok: true; room: RoomInfo} | {ok: false; message: string}> {
  try {
    const res = await fetch('/api/rooms', {
      method: 'POST',
      credentials: 'include',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        name: payload.name.trim(),
        isPrivate: payload.isPrivate,
        password: payload.isPrivate ? payload.password ?? '' : undefined,
      }),
    });
    const body = await res.json().catch(() => null);
    if (!res.ok || !body?.success || !body?.data?.code) {
      return {
        ok: false,
        message: body?.msg || '创建房间失败，请稍后重试',
      };
    }
    return {
      ok: true,
      room: {
        code: String(body.data.code),
        name: String(body.data.name),
        isPrivate: Boolean(body.data.isPrivate),
      },
    };
  } catch {
    return {ok: false, message: '无法连接服务器，请检查网络后重试'};
  }
}

/** 按房间号查询房间是否存在及是否私密 */
export async function lookupRoomApi(
  code: string
): Promise<{ok: true; result: RoomLookupResult} | {ok: false; message: string}> {
  const trimmed = code.trim();
  if (!trimmed) {
    return {ok: false, message: '请输入房间号'};
  }
  try {
    const res = await fetch(`/api/rooms/${encodeURIComponent(trimmed)}`, {
      credentials: 'include',
    });
    const body = await res.json().catch(() => null);
    if (!res.ok || !body?.success || !body?.data) {
      return {
        ok: false,
        message: body?.msg || '查询房间失败，请稍后重试',
      };
    }
    const d = body.data;
    if (!d.exists) {
      return {ok: true, result: {exists: false}};
    }
    return {
      ok: true,
      result: {
        exists: true,
        code: String(d.code),
        name: String(d.name),
        isPrivate: Boolean(d.isPrivate),
        onlineCount: typeof d.onlineCount === 'number' ? d.onlineCount : 0,
      },
    };
  } catch {
    return {ok: false, message: '无法连接服务器，请检查网络后重试'};
  }
}

const currentUser = ref<OnlineUser | null>(null);
const currentRoom = ref<RoomInfo | null>(null);
const lastProfile = ref<SavedProfile | null>(null);
const messages = ref<ChatMessage[]>([]);
const onlineUsers = ref<OnlineUser[]>([]);
const status = ref<ConnectionStatus>('idle');
const isLoadingHistory = ref(false);
const errorMessage = ref<string | null>(null);
const errorCode = ref<string | null>(null);

let socket: WebSocket | null = null;
let shouldAutoReconnect = false;
let reconnectAttempt = 0;
let reconnectTimer: number | null = null;
let heartbeatTimer: number | null = null;
let pendingJoin: JoinRoomPayload | null = null;
let errorClearTimer: number | null = null;

function setTransientError(msg: string, code: string | null = null): void {
  errorMessage.value = msg;
  errorCode.value = code;
  if (errorClearTimer !== null) {
    window.clearTimeout(errorClearTimer);
  }
  errorClearTimer = window.setTimeout(() => {
    errorMessage.value = null;
    errorCode.value = null;
    errorClearTimer = null;
  }, 4500);
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

function closeSocketSilently(): void {
  if (!socket) return;
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
  if (!shouldAutoReconnect || !pendingJoin) return;
  if (reconnectTimer !== null) return;

  status.value = 'reconnecting';
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempt), 10000);
  reconnectAttempt += 1;

  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    if (!shouldAutoReconnect || !pendingJoin) return;
    openSocket(pendingJoin, false).then((res) => {
      if (!res.ok && shouldAutoReconnect) {
        scheduleReconnect();
      }
    });
  }, delay);
}

function openSocket(payload: JoinRoomPayload, isInitialJoin: boolean): Promise<JoinResult> {
  return new Promise((resolve) => {
    closeSocketSilently();

    let settled = false;
    const finish = (result: JoinResult) => {
      if (!settled) {
        settled = true;
        resolve(result);
      }
    };

    if (isInitialJoin) {
      status.value = 'connecting';
      isLoadingHistory.value = true;
      errorMessage.value = null;
      errorCode.value = null;
    }

    let ws: WebSocket;
    try {
      ws = new WebSocket(getWebSocketUrl());
    } catch {
      status.value = 'disconnected';
      isLoadingHistory.value = false;
      const msg = '无法建立实时连接，请检查网络后重试';
      setTransientError(msg);
      finish({ok: false, message: msg});
      return;
    }

    socket = ws;

    const connectTimeout = window.setTimeout(() => {
      if (!settled) {
        closeSocketSilently();
        status.value = isInitialJoin ? 'idle' : 'disconnected';
        isLoadingHistory.value = false;
        const msg = '连接超时，请稍后重试';
        setTransientError(msg);
        finish({ok: false, message: msg});
      }
    }, 8000);

    ws.onopen = () => {
      const joinFrame: Record<string, string> = {
        type: 'join',
        nickname: payload.nickname,
        avatarColor: payload.avatarColor,
        roomCode: payload.roomCode,
      };
      if (payload.password !== undefined) {
        joinFrame.password = payload.password;
      }
      ws.send(JSON.stringify(joinFrame));
    };

    ws.onmessage = (event) => {
      let frame: ServerEvent;
      try {
        frame = JSON.parse(String(event.data)) as ServerEvent;
      } catch {
        return;
      }

      if (frame.type === 'sync:init') {
        window.clearTimeout(connectTimeout);
        currentRoom.value = frame.room;
        currentUser.value = frame.self;
        lastProfile.value = {
          nickname: frame.self.nickname,
          avatarColor: frame.self.avatarColor,
        };
        onlineUsers.value = frame.onlineUsers;
        mergeHistoryIdempotent(frame.messages);
        status.value = 'connected';
        isLoadingHistory.value = false;
        reconnectAttempt = 0;
        errorMessage.value = null;
        errorCode.value = null;
        recordRecentRoomCookie(frame.room.code);
        setRoomCodeInUrl(frame.room.code);
        startHeartbeat();
        finish({ok: true});
        return;
      }

      if (frame.type === 'message:new') {
        if (frame.roomCode && currentRoom.value && frame.roomCode !== currentRoom.value.code) {
          return;
        }
        upsertMessageIdempotent(frame.message);
        return;
      }

      if (frame.type === 'presence:update') {
        if (frame.roomCode && currentRoom.value && frame.roomCode !== currentRoom.value.code) {
          return;
        }
        onlineUsers.value = frame.onlineUsers;
        return;
      }

      if (frame.type === 'error') {
        window.clearTimeout(connectTimeout);
        setTransientError(frame.message, frame.code ?? null);
        if (frame.code === 'ROOM_NOT_FOUND' || frame.code === 'INVALID_PASSWORD') {
          shouldAutoReconnect = false;
        }
        if (!settled) {
          closeSocketSilently();
          isLoadingHistory.value = false;
          status.value = 'idle';
          finish({ok: false, code: frame.code, message: frame.message});
        }
      }
    };

    ws.onerror = () => {
      window.clearTimeout(connectTimeout);
      if (!settled) {
        isLoadingHistory.value = false;
        const msg = '连接聊天服务器失败，请稍后重试';
        if (isInitialJoin) {
          status.value = 'idle';
          setTransientError(msg);
        }
        finish({ok: false, message: msg});
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
        finish({ok: false, message: '连接已关闭'});
      }
      if (shouldAutoReconnect && currentUser.value && currentRoom.value) {
        scheduleReconnect();
      } else if (!currentUser.value || !currentRoom.value) {
        status.value = 'idle';
      } else {
        status.value = 'disconnected';
      }
    };
  });
}

export function useChatSocket() {
  async function joinRoom(payload: JoinRoomPayload): Promise<JoinResult> {
    const normalized: JoinRoomPayload = {
      nickname: payload.nickname.trim(),
      avatarColor: payload.avatarColor,
      roomCode: payload.roomCode.trim(),
      password: payload.password,
    };
    pendingJoin = normalized;
    shouldAutoReconnect = true;
    reconnectAttempt = 0;

    const result = await openSocket(normalized, true);
    if (!result.ok) {
      shouldAutoReconnect = false;
      pendingJoin = null;
    }
    return result;
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
    if (!pendingJoin) return;
    clearTimers();
    shouldAutoReconnect = true;
    reconnectAttempt = 0;
    status.value = 'reconnecting';
    openSocket(pendingJoin, false).then((res) => {
      if (!res.ok && shouldAutoReconnect) {
        scheduleReconnect();
      }
    });
  }

  function leaveRoom(): void {
    shouldAutoReconnect = false;
    pendingJoin = null;
    clearTimers();

    if (socket) {
      if (socket.readyState === WebSocket.OPEN) {
        try {
          socket.send(JSON.stringify({type: 'leave'}));
        } catch {
          // ignore
        }
      }
      closeSocketSilently();
    }

    currentUser.value = null;
    currentRoom.value = null;
    onlineUsers.value = [];
    messages.value = [];
    status.value = 'idle';
    isLoadingHistory.value = false;
    errorMessage.value = null;
    errorCode.value = null;
    setRoomCodeInUrl(null);
  }

  function clearError(): void {
    errorMessage.value = null;
    errorCode.value = null;
  }

  function clearLastProfile(): void {
    lastProfile.value = null;
  }

  return {
    currentUser,
    currentRoom,
    lastProfile,
    messages,
    onlineUsers,
    status,
    isLoadingHistory,
    errorMessage,
    errorCode,
    joinRoom,
    sendMessage,
    manualReconnect,
    leaveRoom,
    clearError,
    clearLastProfile,
  };
}
