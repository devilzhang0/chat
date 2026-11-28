export type MessageToken =
  | {kind: 'text'; text: string}
  | {kind: 'code'; text: string}
  | {kind: 'link'; href: string; label: string}
  | {kind: 'mention'; text: string; targetName: string; isSelf: boolean};

export interface ParseMessageOptions {
  selfNickname?: string | null;
  knownNames?: string[];
}

const MAX_LINK_LABEL_LEN = 56;

// 优先匹配单行反引号行内代码，其次匹配 http(s) 超链接（遇空白、引号、尖括号或常见中文标点停止）
const TOKEN_REGEX =
  /`([^`\n]+)`|(https?:\/\/[^\s<>"'`\u3000\u3001-\u303F\uFF01-\uFF0F\uFF1A-\uFF20\uFF3B-\uFF40\uFF5B-\uFF65]+)/g;

const TRAILING_PUNCT_RE = /[.,!?;:'"~]$/;
const MENTION_BEFORE_RE = /[\s,，。！？；：、()[\]【】<>“”‘’]/;
const MENTION_AFTER_RE = /[\s,，。！？；：、()[\]【】<>“”‘’.:!?;'"]/;
const FALLBACK_MENTION_RE =
  /(^|[\s,，。！？；：、()[\]【】])@([^\s@.,!?;:'"，。！？；：、()[\]【】]{1,20})/g;

function countChar(str: string, ch: string): number {
  let count = 0;
  for (let i = 0; i < str.length; i++) {
    if (str[i] === ch) count += 1;
  }
  return count;
}

/** 剥离链接末尾误粘连的标点与不成对的右括号，返回 [纯净URL, 尾部残留文本] */
function trimTrailingPunctuation(rawUrl: string): [string, string] {
  let url = rawUrl;
  let trailing = '';

  while (url.length > 0) {
    const last = url[url.length - 1];
    if (TRAILING_PUNCT_RE.test(last)) {
      trailing = last + trailing;
      url = url.slice(0, -1);
      continue;
    }
    if (last === ')' && countChar(url, ')') > countChar(url, '(')) {
      trailing = last + trailing;
      url = url.slice(0, -1);
      continue;
    }
    if (last === ']' && countChar(url, ']') > countChar(url, '[')) {
      trailing = last + trailing;
      url = url.slice(0, -1);
      continue;
    }
    if (last === '}' && countChar(url, '}') > countChar(url, '{')) {
      trailing = last + trailing;
      url = url.slice(0, -1);
      continue;
    }
    break;
  }

  return [url, trailing];
}

function isSafeHttpUrl(candidate: string): boolean {
  try {
    const parsed = new URL(candidate);
    return (
      (parsed.protocol === 'http:' || parsed.protocol === 'https:') &&
      parsed.hostname.length > 0
    );
  } catch {
    return false;
  }
}

function formatLinkLabel(href: string): string {
  const chars = Array.from(href);
  if (chars.length <= MAX_LINK_LABEL_LEN) {
    return href;
  }
  return `${chars.slice(0, 42).join('')}…${chars.slice(-10).join('')}`;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function splitMentionsInText(text: string, options?: ParseMessageOptions): MessageToken[] {
  if (!text.includes('@')) {
    return [{kind: 'text', text}];
  }

  const selfName = options?.selfNickname?.trim() || '';
  const uniqueKnown = Array.from(
    new Set(
      [...(options?.knownNames ?? []), ...(selfName ? [selfName] : [])]
        .map((n) => n.trim())
        .filter((n) => n.length > 0)
    )
  ).sort((a, b) => b.length - a.length);

  // 先识别已知房间成员（支持昵称含空格），再兜底识别历史离线成员的 @昵称
  const tokens: MessageToken[] = [];
  let cursor = 0;

  while (cursor < text.length) {
    const atIndex = text.indexOf('@', cursor);
    if (atIndex === -1) {
      tokens.push({kind: 'text', text: text.slice(cursor)});
      break;
    }

    const validBefore = atIndex === 0 || MENTION_BEFORE_RE.test(text[atIndex - 1]);
    if (!validBefore) {
      tokens.push({kind: 'text', text: text.slice(cursor, atIndex + 1)});
      cursor = atIndex + 1;
      continue;
    }

    let matchedName: string | null = null;
    for (const candidate of uniqueKnown) {
      if (text.startsWith(candidate, atIndex + 1)) {
        const afterIdx = atIndex + 1 + candidate.length;
        if (afterIdx === text.length || MENTION_AFTER_RE.test(text[afterIdx])) {
          matchedName = candidate;
          break;
        }
      }
    }

    if (!matchedName) {
      FALLBACK_MENTION_RE.lastIndex = atIndex === 0 ? 0 : atIndex - 1;
      const m = FALLBACK_MENTION_RE.exec(text);
      if (m && m.index + m[1].length === atIndex) {
        matchedName = m[2];
      }
    }

    if (matchedName) {
      if (atIndex > cursor) {
        tokens.push({kind: 'text', text: text.slice(cursor, atIndex)});
      }
      const mentionText = `@${matchedName}`;
      tokens.push({
        kind: 'mention',
        text: mentionText,
        targetName: matchedName,
        isSelf: Boolean(selfName && matchedName === selfName),
      });
      cursor = atIndex + mentionText.length;
    } else {
      tokens.push({kind: 'text', text: text.slice(cursor, atIndex + 1)});
      cursor = atIndex + 1;
    }
  }

  return tokens;
}

/**
 * 纯文本安全解析器（绝不使用 v-html）：
 * 将原始消息拆分为普通文本、http(s) 超链接、行内代码与 @成员提及数组。
 */
export function parseMessageContent(
  content: string,
  options?: ParseMessageOptions
): MessageToken[] {
  if (!content) return [];

  const tokens: MessageToken[] = [];
  let cursor = 0;

  TOKEN_REGEX.lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = TOKEN_REGEX.exec(content)) !== null) {
    const matchStart = match.index;
    if (matchStart > cursor) {
      tokens.push(...splitMentionsInText(content.slice(cursor, matchStart), options));
    }

    const codeInner = match[1];
    const rawUrl = match[2];

    if (codeInner !== undefined) {
      tokens.push({kind: 'code', text: codeInner});
    } else if (rawUrl !== undefined) {
      const [cleanUrl, trailing] = trimTrailingPunctuation(rawUrl);
      if (isSafeHttpUrl(cleanUrl)) {
        tokens.push({
          kind: 'link',
          href: cleanUrl,
          label: formatLinkLabel(cleanUrl),
        });
        if (trailing) {
          tokens.push(...splitMentionsInText(trailing, options));
        }
      } else {
        tokens.push(...splitMentionsInText(rawUrl, options));
      }
    }

    cursor = TOKEN_REGEX.lastIndex;
  }

  if (cursor < content.length) {
    tokens.push(...splitMentionsInText(content.slice(cursor), options));
  }

  return tokens;
}

/** 检测一条文本消息是否提及了指定昵称（忽略代码块与链接中的 @） */
export function isMentioningUser(
  content: string,
  selfNickname: string | null | undefined,
  knownNames?: string[]
): boolean {
  const target = selfNickname?.trim();
  if (!target || !content || !content.includes('@')) {
    return false;
  }
  const tokens = parseMessageContent(content, {selfNickname: target, knownNames});
  return tokens.some((t) => t.kind === 'mention' && t.isSelf);
}

/** 构造单行引用预览摘要（前端乐观渲染用，与后端 toReplySnippet 保持一致） */
export function makeReplySnippet(content: string, maxLen = 60): string {
  const singleLine = content.replace(/\s+/g, ' ').trim();
  const chars = Array.from(singleLine);
  if (chars.length <= maxLen) return singleLine;
  return `${chars.slice(0, maxLen).join('')}…`;
}

// 保留工具导出便于测试或后续扩展
export {escapeRegExp};
