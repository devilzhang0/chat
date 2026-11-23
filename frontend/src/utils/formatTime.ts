export function formatMessageTime(timestamp: number): string {
  const date = new Date(timestamp);
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function formatDateKey(timestamp: number): string {
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatDateDivider(timestamp: number): string {
  const target = new Date(timestamp);
  const now = new Date();

  const targetDay = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const dayMs = 24 * 60 * 60 * 1000;

  if (targetDay === today) {
    return '今天';
  }
  if (targetDay === today - dayMs) {
    return '昨天';
  }
  return `${target.getFullYear()}年${target.getMonth() + 1}月${target.getDate()}日`;
}
