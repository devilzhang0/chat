<script setup lang="ts">
import ChatView from '@/components/ChatView.vue';
import LoginView from '@/components/LoginView.vue';
import {useChatSocket} from '@/composables/useChatSocket';
import {useTheme} from '@/composables/useTheme';

// Initialize global theme synchronization
useTheme();

const {currentUser} = useChatSocket();
</script>

<template>
  <Transition name="view-fade" mode="out-in">
    <ChatView v-if="currentUser" key="chat" />
    <LoginView v-else key="login" />
  </Transition>
</template>
