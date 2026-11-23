import {computed, ref} from 'vue';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'purechat_theme';

function detectInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'light';
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') {
      return saved;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

const theme = ref<ThemeMode>(detectInitialTheme());

function applyTheme(mode: ThemeMode): void {
  if (typeof document === 'undefined') return;
  document.documentElement.classList.toggle('dark', mode === 'dark');
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // Ignore storage errors in restricted contexts
  }
}

// Ensure DOM matches initial reactive value
applyTheme(theme.value);

export function useTheme() {
  const isDark = computed(() => theme.value === 'dark');

  function setTheme(mode: ThemeMode): void {
    theme.value = mode;
    applyTheme(mode);
  }

  function toggleTheme(): void {
    setTheme(theme.value === 'dark' ? 'light' : 'dark');
  }

  return {
    theme,
    isDark,
    setTheme,
    toggleTheme,
  };
}
