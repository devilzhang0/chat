import {onBeforeUnmount, onMounted, ref} from 'vue';

/**
 * 适配移动端浏览器虚拟键盘（iOS Safari / Android Chrome / 微信 WebView）：
 * - 实时追踪 visualViewport 的真实可见高度与 offsetTop，防止键盘弹出时顶栏被顶出屏幕或底部错位；
 * - 识别键盘展开/收起状态，动态控制安全区留白与自动滚底；
 * - 键盘收起后重置 iOS Safari 可能残留的页面滚动偏移。
 */
export function useMobileViewport(onKeyboardOpen?: () => void) {
  const viewportHeight = ref<number | null>(null);
  const viewportOffsetTop = ref<number>(0);
  const isKeyboardOpen = ref<boolean>(false);

  let baselineHeight = 0;
  let rafId: number | null = null;
  let resetTimer: number | null = null;

  function syncViewport(): void {
    const vv = window.visualViewport;
    const layoutHeight = window.innerHeight;

    if (!vv) {
      viewportHeight.value = null;
      viewportOffsetTop.value = 0;
      isKeyboardOpen.value = false;
      return;
    }

    // 记录无键盘时的基准视口高度（横竖屏切换时允许重置）
    if (vv.height > baselineHeight || Math.abs(layoutHeight - baselineHeight) > 300) {
      baselineHeight = Math.max(layoutHeight, vv.height);
    }

    const nextHeight = Math.round(vv.height);
    const nextOffsetTop = Math.max(0, Math.round(vv.offsetTop));
    const heightDiff = Math.max(layoutHeight - vv.height, baselineHeight - vv.height);
    const wasOpen = isKeyboardOpen.value;
    const nextOpen = heightDiff > 120;

    viewportHeight.value = nextHeight;
    viewportOffsetTop.value = nextOffsetTop;
    isKeyboardOpen.value = nextOpen;

    // 键盘刚展开时回调（例如自动将聊天流滚到最新消息）
    if (!wasOpen && nextOpen && onKeyboardOpen) {
      onKeyboardOpen();
    }

    // 键盘收起后清理 iOS Safari 可能残留的整页滚动偏移
    if (wasOpen && !nextOpen) {
      if (resetTimer !== null) {
        window.clearTimeout(resetTimer);
      }
      resetTimer = window.setTimeout(() => {
        resetTimer = null;
        if (window.scrollY !== 0) {
          window.scrollTo(0, 0);
        }
        if (window.visualViewport) {
          viewportOffsetTop.value = Math.max(0, Math.round(window.visualViewport.offsetTop));
          viewportHeight.value = Math.round(window.visualViewport.height);
        }
      }, 60);
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

  onMounted(() => {
    baselineHeight = window.innerHeight;
    syncViewport();

    const vv = window.visualViewport;
    if (vv) {
      vv.addEventListener('resize', scheduleSync);
      vv.addEventListener('scroll', scheduleSync);
    }
    window.addEventListener('resize', scheduleSync);
    window.addEventListener('orientationchange', handleOrientationChange);
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
  });

  return {
    viewportHeight,
    viewportOffsetTop,
    isKeyboardOpen,
    syncViewport,
  };
}
