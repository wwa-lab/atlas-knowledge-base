<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <p class="app-title">Atlas Knowledge Base</p>
      <nav aria-label="Primary">
        <RouterLink to="/chat">Chat</RouterLink>
        <RouterLink to="/kbs">Knowledge bases</RouterLink>
        <RouterLink to="/settings">Settings</RouterLink>
        <RouterLink to="/register">Owner wizard</RouterLink>
        <RouterLink to="/admin">Admin</RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
