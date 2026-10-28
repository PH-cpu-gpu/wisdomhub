<script setup lang="ts">
import WhButton from './WhButton.vue'
import WhModal from './WhModal.vue'
import { confirmState, resolveConfirm } from './confirm'
</script>

<template>
  <WhModal
    v-model="confirmState.open"
    :title="confirmState.title"
    size="sm"
    @close="resolveConfirm(false)"
  >
    <p class="wh-confirm__message">{{ confirmState.message }}</p>

    <template #footer>
      <WhButton variant="ghost" @click="resolveConfirm(false)">
        {{ confirmState.cancelText }}
      </WhButton>
      <WhButton :variant="confirmState.danger ? 'danger' : 'primary'" @click="resolveConfirm(true)">
        {{ confirmState.confirmText }}
      </WhButton>
    </template>
  </WhModal>
</template>

<style scoped>
.wh-confirm__message {
  margin: 0;
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}
</style>
