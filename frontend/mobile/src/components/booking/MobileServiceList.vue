<script setup lang="ts">
import { categoryLabels, price, type Catalog, type Category } from '../../api/bookings'
defineProps<{
  rows: Catalog[]
  page: number
  total: number
  busy: boolean
  category: Category | ''
}>()
const emit = defineEmits<{
  select: [service: Catalog]
  page: [page: number]
  filter: [category: Category | '']
}>()
</script>
<template>
  <view>
    <view class="mobile-filter"
      ><button :disabled="busy" :class="{ 'font-button': !category }" @click="emit('filter', '')">
        全部</button
      ><button
        v-for="(label, value) in categoryLabels"
        :key="value"
        :disabled="busy"
        :class="{ 'font-button': category === value }"
        @click="emit('filter', value)"
      >
        {{ label }}
      </button></view
    >
    <view v-for="service in rows" :key="service.id" class="mobile-card"
      ><text class="mobile-chip">{{ categoryLabels[service.category] }}</text
      ><text class="card-title">{{ service.name }}</text
      ><text class="body-copy">{{ service.description }}</text
      ><text class="mobile-details"
        >{{ service.durationMinutes }} 分钟 · 参考价 ¥{{ price(service.priceFen) }}\n服务范围：{{
          service.serviceArea
        }}\n提供方：{{ service.providerName }}{{ service.professional ? ' · 专业服务' : '' }}</text
      ><button class="feature-button" :disabled="busy" @click="emit('select', service)">
        查看并申请预约
      </button></view
    >
    <view v-if="!rows.length && !busy" class="mobile-card">暂无可预约的服务，请联系社区咨询。</view>
    <view class="mobile-buttons"
      ><button :disabled="busy || page <= 1" @click="emit('page', page - 1)">上一页</button
      ><text>第 {{ page }} 页，共 {{ total }} 项</text
      ><button :disabled="busy || page * 10 >= total" @click="emit('page', page + 1)">
        下一页
      </button></view
    >
  </view>
</template>
