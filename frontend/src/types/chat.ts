export type AvatarColorKey = 'indigo' | 'slate' | 'emerald' | 'amber' | 'rose' | 'cyan';

export interface AvatarColorOption {
  key: AvatarColorKey;
  label: string;
  bgClass: string;
  swatchClass: string;
  ringClass: string;
}

export const AVATAR_COLORS: AvatarColorOption[] = [
  {
    key: 'cyan',
    label: '晴蓝',
    bgClass: 'bg-gradient-to-b from-[#3395FF] to-[#007AFF] text-white dark:from-[#2997FF] dark:to-[#0A84FF]',
    swatchClass: 'bg-[#007AFF] dark:bg-[#0A84FF]',
    ringClass: 'ring-[#007AFF] dark:ring-[#0A84FF]',
  },
  {
    key: 'indigo',
    label: '靛蓝',
    bgClass: 'bg-gradient-to-b from-[#7876EA] to-[#5856D6] text-white dark:from-[#7D7AFF] dark:to-[#5E5CE6]',
    swatchClass: 'bg-[#5856D6] dark:bg-[#5E5CE6]',
    ringClass: 'ring-[#5856D6] dark:ring-[#5E5CE6]',
  },
  {
    key: 'emerald',
    label: '翠绿',
    bgClass: 'bg-gradient-to-b from-[#4CD964] to-[#34C759] text-white dark:from-[#40E068] dark:to-[#30D158]',
    swatchClass: 'bg-[#34C759] dark:bg-[#30D158]',
    ringClass: 'ring-[#34C759] dark:ring-[#30D158]',
  },
  {
    key: 'amber',
    label: '暖橙',
    bgClass: 'bg-gradient-to-b from-[#FFAA33] to-[#FF9500] text-white dark:from-[#FFB340] dark:to-[#FF9F0A]',
    swatchClass: 'bg-[#FF9500] dark:bg-[#FF9F0A]',
    ringClass: 'ring-[#FF9500] dark:ring-[#FF9F0A]',
  },
  {
    key: 'rose',
    label: '绯红',
    bgClass: 'bg-gradient-to-b from-[#FF4D6D] to-[#FF2D55] text-white dark:from-[#FF5C7A] dark:to-[#FF375F]',
    swatchClass: 'bg-[#FF2D55] dark:bg-[#FF375F]',
    ringClass: 'ring-[#FF2D55] dark:ring-[#FF375F]',
  },
  {
    key: 'slate',
    label: '石墨',
    bgClass: 'bg-gradient-to-b from-[#7C7C82] to-[#5C5C60] text-white dark:from-[#6E6E73] dark:to-[#48484A]',
    swatchClass: 'bg-[#636366] dark:bg-[#636366]',
    ringClass: 'ring-[#636366] dark:ring-[#8E8E93]',
  },
];

export function getAvatarBgClass(color: string): string {
  const found = AVATAR_COLORS.find((c) => c.key === color);
  return found ? found.bgClass : AVATAR_COLORS[0].bgClass;
}

export function getAvatarInitials(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return '?';
  const chars = Array.from(trimmed);
  if (chars.length === 1) return chars[0].toUpperCase();
  const isFirstAscii = /^[A-Za-z0-9]$/.test(chars[0]);
  const isSecondAscii = /^[A-Za-z0-9]$/.test(chars[1]);
  if (isFirstAscii && isSecondAscii) {
    return (chars[0] + chars[1]).toUpperCase();
  }
  return chars[0].toUpperCase();
}

export interface OnlineUser {
  id: string;
  nickname: string;
  avatarColor: AvatarColorKey;
  joinedAt: number;
}

export interface RoomInfo {
  code: string;
  name: string;
  isPrivate: boolean;
  onlineCount?: number;
}

export interface RoomLookupResult {
  exists: boolean;
  code?: string;
  name?: string;
  isPrivate?: boolean;
  onlineCount?: number;
}

export interface CreateRoomPayload {
  name: string;
  isPrivate: boolean;
  password?: string;
}

export interface JoinRoomPayload {
  nickname: string;
  avatarColor: AvatarColorKey;
  roomCode: string;
  password?: string;
}

export type MessageSendStatus = 'sending' | 'sent' | 'failed';

export interface ReplySnapshot {
  id: string;
  senderName: string;
  contentSnippet: string;
}

export interface ChatMessage {
  id: string;
  type: 'text' | 'system';
  senderId: string;
  senderName: string;
  avatarColor: AvatarColorKey;
  content: string;
  timestamp: number;
  replyTo?: ReplySnapshot;
  clientTempId?: string;
  sendStatus?: MessageSendStatus;
  failReason?: string;
}

export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected';

export type ServerEvent =
  | {
      type: 'sync:init';
      room: RoomInfo;
      self: OnlineUser;
      messages: ChatMessage[];
      hasMoreHistory?: boolean;
      onlineUsers: OnlineUser[];
    }
  | {
      type: 'history:page';
      roomCode?: string;
      messages: ChatMessage[];
      hasMore: boolean;
    }
  | {
      type: 'message:new';
      roomCode?: string;
      message: ChatMessage;
      clientTempId?: string;
    }
  | {
      type: 'presence:update';
      roomCode?: string;
      onlineUsers: OnlineUser[];
    }
  | {
      type: 'pong';
      timestamp: number;
    }
  | {
      type: 'error';
      code?: string;
      message: string;
      clientTempId?: string;
    };
