<script setup lang="ts">
import {computed} from 'vue';
import {parseMessageContent} from '@/utils/parseMessage';

const props = defineProps<{
  content: string;
  isSelf?: boolean;
  selfNickname?: string | null;
  knownNames?: string[];
}>();

const tokens = computed(() =>
  parseMessageContent(props.content, {
    selfNickname: props.selfNickname,
    knownNames: props.knownNames,
  })
);
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
    <span
      v-else-if="token.kind === 'mention'"
      class="inline font-semibold px-1 py-0.5 -mx-0.5 rounded-[6px]"
      :class="
        isSelf
          ? 'bg-white/22 text-white'
          : token.isSelf
            ? 'bg-[#FF9500]/20 text-[#B25000] dark:bg-[#FF9F0A]/25 dark:text-[#FFB340]'
            : 'bg-[#007AFF]/12 text-[#0066D6] dark:bg-[#0A84FF]/20 dark:text-[#5AC8FA]'
      "
    >{{ token.text }}</span>
    <template v-else>{{ token.text }}</template>
  </template>
</template>
