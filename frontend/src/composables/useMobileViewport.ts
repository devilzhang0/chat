import {onBeforeUnmount, onMounted, ref} from 'vue';

/**
 * 适配移动端浏览器与 PWA 独立全屏模式下的虚拟键盘（iOS Safari / Android Chrome / 微信 WebView）：
 * - 实时追踪 visualViewport 的真实可见高度与 offsetTop，防止键盘弹出时顶栏被顶出屏幕或底部错位；
 * - 识别键盘展开/收起状态，动态控制底部安全区留白与自动滚底，并同步 --app-vh / --keyboard-height CSS 变量；
 * - 监听键盘收起与 focusout，清理 iOS Safari / Standalone PWA 可能残留的页面滚动偏移。
 */
export function useMobileViewport(onKeyboardOpen?: () => void) {
  const viewportHeight = ref<number | null>(null);
  const viewportOffsetTop = ref<number>(0);
  const isKeyboardOpen = ref<boolean>(false);
  const isStandalone = ref<boolean>(false);

  let baselineHeight = 0;
  let rafId: number | null = null;
  let resetTimer: number | null = null;

  function detectStandalone(): boolean {
    if (typeof window === 'undefined') return false;
    const nav = window.navigator as Navigator & {standalone?: boolean};
    return Boolean(
      window.matchMedia?.('(display-mode: standalone)').matches || nav.standalone === true
    );
  }

  function updateCssVars(heightPx: number | null, keyboardPx: number): void {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    if (heightPx !== null) {
      root.style.setProperty('--app-vh', `${heightPx}px`);
    } else {
      root.style.removeProperty('--app-vh');
    }
    root.style.setProperty('--keyboard-height', `${Math.max(0, Math.round(keyboardPx))}px`);
  }

  function resetPageScrollOffset(): void {
    if (resetTimer !== null) {
      window.clearTimeout(resetTimer);
    }
    resetTimer = window.setTimeout(() => {
      resetTimer = null;
      if (window.scrollY !== 0) {
        window.scrollTo(0, 0);
      }
      const vv = window.visualViewport;
      if (vv) {
        viewportOffsetTop.value = Math.max(0, Math.round(vv.offsetTop));
        viewportHeight.value = Math.round(vv.height);
        updateCssVars(viewportHeight.value, 0);
      }
    }, 80);
  }

  function syncViewport(): void {
    const vv = window.visualViewport;
    const layoutHeight = window.innerHeight;

    if (!vv) {
      viewportHeight.value = null;
      viewportOffsetTop.value = 0;
      isKeyboardOpen.value = false;
      updateCssVars(null, 0);
      return;
    }

    // 忽略双指缩放状态，避免误判为键盘弹出
    const isPinchZoomed = typeof vv.scale === 'number' && vv.scale > 1.05;

    // 记录无键盘时的基准视口高度（横竖屏切换时允许重置）
    if (
      !isPinchZoomed &&
      (vv.height > baselineHeight || Math.abs(layoutHeight - baselineHeight) > 300)
    ) {
      baselineHeight = Math.max(layoutHeight, vv.height);
    }

    const nextHeight = Math.round(vv.height);
    const nextOffsetTop = Math.max(0, Math.round(vv.offsetTop));
    const heightDiff = isPinchZoomed
      ? 0
      : Math.max(layoutHeight - vv.height, baselineHeight - vv.height);
    const wasOpen = isKeyboardOpen.value;
    const nextOpen = heightDiff > 120;

    viewportHeight.value = nextHeight;
    viewportOffsetTop.value = nextOffsetTop;
    isKeyboardOpen.value = nextOpen;
    updateCssVars(nextHeight, nextOpen ? heightDiff : 0);

    // 键盘刚展开时回调（例如自动将聊天流滚到最新消息）
    if (!wasOpen && nextOpen && onKeyboardOpen) {
      onKeyboardOpen();
    }

    // 键盘收起后清理 iOS Safari / PWA 可能残留的整页滚动偏移
    if (wasOpen && !nextOpen) {
      resetPageScrollOffset();
    }
  }

  function scheduleSync(): void {
    if (rafId !== null) {
      window.cancelAnimationFrame(rafId);
    }
    rafId = window.requestAnimationFrame(() => {
      rafId = null;
      syncViewport();
    });
  }

  function handleOrientationChange(): void {
    baselineHeight = 0;
    scheduleSync();
  }

  function handleFocusOut(): void {
    // iOS 软键盘点击「完成」收起后，延迟校准一次视口与 scrollY
    window.setTimeout(() => {
      const active = document.activeElement;
      const stillEditing =
        active instanceof HTMLElement &&
        (active.tagName === 'INPUT' ||
          active.tagName === 'TEXTAREA' ||
          active.isContentEditable);
      if (!stillEditing) {
        syncViewport();
        if (!isKeyboardOpen.value && window.scrollY !== 0) {
          resetPageScrollOffset();
        }
      }
    }, 100);
  }

  onMounted(() => {
    isStandalone.value = detectStandalone();
    baselineHeight = window.innerHeight;
    syncViewport();

    const vv = window.visualViewport;
    if (vv) {
      vv.addEventListener('resize', scheduleSync);
      vv.addEventListener('scroll', scheduleSync);
    }
    window.addEventListener('resize', scheduleSync);
    window.addEventListener('orientationchange', handleOrientationChange);
    document.addEventListener('focusout', handleFocusOut);
  });

  onBeforeUnmount(() => {
    if (rafId !== null) {
      window.cancelAnimationFrame(rafId);
      rafId = null;
    }
    if (resetTimer !== null) {
      window.clearTimeout(resetTimer);
      resetTimer = null;
    }
    const vv = window.visualViewport;
    if (vv) {
      vv.removeEventListener('resize', scheduleSync);
      vv.removeEventListener('scroll', scheduleSync);
    }
    window.removeEventListener('resize', scheduleSync);
    window.removeEventListener('orientationchange', handleOrientationChange);
    document.removeEventListener('focusout', handleFocusOut);
    updateCssVars(null, 0);
  });

  return {
    viewportHeight,
    viewportOffsetTop,
    isKeyboardOpen,
    isStandalone,
    syncViewport,
  };
}
