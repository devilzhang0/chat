<script setup lang="ts">
import {computed} from 'vue';
import {parseMessageContent} from '@/utils/parseMessage';

const props = defineProps<{
  content: string;
  isSelf?: boolean;
}>();

const tokens = computed(() => parseMessageContent(props.content));
</script>

<template>
  <template v-for="(token, idx) in tokens" :key="idx">
    <a
      v-if="token.kind === 'link'"
      :href="token.href"
      :title="token.href"
      target="_blank"
      rel="noopener noreferrer"
      class="underline underline-offset-2 break-all transition-opacity"
      :class="
        isSelf
          ? 'text-white decoration-white/70 hover:decoration-white'
          : 'text-[#0066D6] dark:text-[#409CFF] decoration-current/45 hover:decoration-current'
      "
    >{{ token.label }}</a>
    <code
      v-else-if="token.kind === 'code'"
      class="font-mono text-[13px] leading-snug px-1.5 py-0.5 mx-0.5 rounded-[6px] break-all"
      :class="
        isSelf
          ? 'bg-white/20 text-white'
          : 'bg-black/[0.07] text-[#1C1C1E] dark:bg-white/[0.12] dark:text-[#F5F5F7]'
      "
    >{{ token.text }}</code>
    <template v-else>{{ token.text }}</template>
  </template>
</template>
