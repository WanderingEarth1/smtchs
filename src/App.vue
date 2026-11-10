<script setup lang="ts">
import ToastHost from '@/components/ToastHost.vue'
</script>

<template>
  <RouterView v-slot="{ Component, route }">
    <Transition name="fade" mode="out-in">
      <component :is="Component" :key="route.path" />
    </Transition>
  </RouterView>
  <ToastHost />
</template>
