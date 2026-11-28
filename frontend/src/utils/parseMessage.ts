export type MessageToken =
  | {kind: 'text'; text: string}
  | {kind: 'code'; text: string}
  | {kind: 'link'; href: string; label: string};

const MAX_LINK_LABEL_LEN = 56;

// 优先匹配单行反引号行内代码，其次匹配 http(s) 超链接（遇空白、引号、尖括号或常见中文标点停止）
const TOKEN_REGEX =
  /`([^`\n]+)`|(https?:\/\/[^\s<>"'`\u3000\u3001-\u303F\uFF01-\uFF0F\uFF1A-\uFF20\uFF3B-\uFF40\uFF5B-\uFF65]+)/g;

const TRAILING_PUNCT_RE = /[.,!?;:'"~]$/;

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

/**
 * 纯文本安全解析器（绝不使用 v-html）：
 * 将原始消息拆分为普通文本、http(s) 超链接与行内代码片段数组。
 */
export function parseMessageContent(content: string): MessageToken[] {
  if (!content) return [];

  const tokens: MessageToken[] = [];
  let cursor = 0;

  TOKEN_REGEX.lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = TOKEN_REGEX.exec(content)) !== null) {
    const matchStart = match.index;
    if (matchStart > cursor) {
      tokens.push({kind: 'text', text: content.slice(cursor, matchStart)});
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
          tokens.push({kind: 'text', text: trailing});
        }
      } else {
        tokens.push({kind: 'text', text: rawUrl});
      }
    }

    cursor = TOKEN_REGEX.lastIndex;
  }

  if (cursor < content.length) {
    tokens.push({kind: 'text', text: content.slice(cursor)});
  }

  return tokens;
}
