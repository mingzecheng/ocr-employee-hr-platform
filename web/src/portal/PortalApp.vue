<script setup lang="ts">
import PortalLayout from '../layouts/PortalLayout.vue'
import type { PortalId } from './portal-config'

defineProps<{ portal: PortalId }>()
</script>

<template><PortalLayout :portal="portal" /></template>
