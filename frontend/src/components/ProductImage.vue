<script setup lang="ts">
/**
 * 商品图 + 无图占位（见 docs/implementation.md §4.4）。
 *
 * 无图时用「按 id 取的粉色渐变块 + 商品名首字」，而不是灰底"暂无图片"——
 * 这样卡片不会塌掉，也能看出"图占大头"的版式效果。
 * 字号由使用它的容器决定（卡片 40px、详情页大图另设）。
 */
const props = defineProps<{
  src?: string | null
  name: string
  id: number
}>()

const GRADIENTS = [
  'linear-gradient(135deg, #FFE4EF, #FFD6E7)',
  'linear-gradient(135deg, #FFE9F0, #FFDCE8)',
  'linear-gradient(135deg, #FFF0F5, #FFE2EC)',
  'linear-gradient(135deg, #FFE6F0, #FFD9E6)',
]
</script>

<template>
  <img v-if="props.src" class="p-img" :src="props.src" :alt="props.name" />
  <div
    v-else
    class="p-img p-ph"
    :style="{ background: GRADIENTS[props.id % GRADIENTS.length] }"
  >
    {{ props.name.trim().charAt(0) }}
  </div>
</template>

<style scoped>
.p-img {
  width: 100%;
  height: 100%;
  display: block;
  /* 容器比例是固定的（卡片 160×自适应、详情页 420 方形），图不能被拉变形 */
  object-fit: cover;
}

.p-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: rgba(232, 99, 143, 0.35);
}
</style>
