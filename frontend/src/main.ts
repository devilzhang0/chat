import {createApp} from 'vue';
import App from './App.vue';
import './index.css';

createApp(App).mount('#app');

// 生产环境注册轻量 Service Worker，支持移动端「添加到主屏幕」以类原生全屏模式运行
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', {scope: '/'}).catch(() => {
      // 静默忽略非 HTTPS 或受限 WebView 环境的注册失败
    });
  });
}
