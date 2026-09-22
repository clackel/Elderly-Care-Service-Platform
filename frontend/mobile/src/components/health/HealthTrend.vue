<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, shallowRef, watch } from 'vue'
import { healthTypes, glucoseScenes, healthTime, measurementText, units, type HealthType, type GlucoseScene, type HealthRecord } from '../../api/health'
const props = defineProps<{ points: HealthRecord[]; disabled: boolean }>()
const emit = defineEmits<{ query: [type: HealthType, scene: GlucoseScene, days: number]; reset: [] }>()
const type = shallowRef<HealthType>('BLOOD_PRESSURE'), scene = shallowRef<GlucoseScene>('FASTING'), days = shallowRef(30)
const requested = shallowRef(false), valuePage = shallowRef(1)
const types = Object.keys(healthTypes) as HealthType[], scenes = Object.keys(glucoseScenes) as GlucoseScene[]
const values = computed(() => props.points.slice((valuePage.value - 1) * 20, valuePage.value * 20))
const instance = getCurrentInstance()
let drawEpoch = 0, alive = true
/** 修改趋势条件先清空旧序列，避免把不同指标或场景混在一起。 */
watch([type, scene, days], () => { requested.value = false; emit('reset') })
/** 按原始测量时间绘制直线及点，不聚合、不插补；血压两条线共用时间点。 */
async function draw() {
  const ticket = ++drawEpoch, source = props.points
  valuePage.value = 1
  if (!source.length) return
  await nextTick()
  if (!alive || ticket !== drawEpoch) return
  const ctx = uni.createCanvasContext('health-trend', instance?.proxy)
  const measurements = source.map(p => p.measurement)
  const sequences = measurements[0]!.type === 'BLOOD_PRESSURE'
    ? [measurements.map(m => m.systolic!), measurements.map(m => m.diastolic!)]
    : [measurements.map(m => (m.heartRate ?? m.weight ?? m.glucose)!)]
  const all = sequences.flat(), min = Math.min(...all), max = Math.max(...all)
  const timestamps = measurements.map(m => new Date(m.measuredAt).getTime()), first = timestamps[0]!, last = timestamps[timestamps.length - 1]!
  ctx.clearRect(0, 0, 280, 180); ctx.setFontSize(12); ctx.setFillStyle('#334d41')
  ctx.fillText(String(max), 2, 16); ctx.fillText(String(min), 2, 150)
  for (let series = 0; series < sequences.length; series++) {
    ctx.setStrokeStyle(series === 0 ? '#27664e' : '#345ea3'); ctx.setFillStyle(series === 0 ? '#27664e' : '#345ea3')
    ctx.setLineDash(series === 0 ? [] : [6, 4], 0)
    ctx.setLineWidth(2); ctx.beginPath()
    sequences[series]!.forEach((value, index) => {
      const x = 40 + (last === first ? 110 : (timestamps[index]! - first) / (last - first) * 225)
      const y = max === min ? 85 : 145 - (value - min) / (max - min) * 125
      if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
    })
    ctx.stroke()
    sequences[series]!.forEach((value, index) => {
      const x = 40 + (last === first ? 110 : (timestamps[index]! - first) / (last - first) * 225)
      const y = max === min ? 85 : 145 - (value - min) / (max - min) * 125
      ctx.beginPath(); ctx.arc(x, y, 3, 0, 2 * Math.PI); ctx.fill()
    })
  }
  ctx.draw()
}
watch(() => props.points, draw, { immediate: true })
/** 销毁图表后拒绝异步绘制旧老人的测量点。 */
onBeforeUnmount(() => { alive = false; drawEpoch++ })
/** 明确发起所选条件的趋势读取。 */
function query() { requested.value = true; emit('query', type.value, scene.value, days.value) }
</script>
<template>
  <view class="mobile-card">
    <text class="card-title">原始测量趋势</text>
    <picker :disabled="disabled" :range="types.map(t => healthTypes[t])" :value="types.indexOf(type)" @change="type = types[Number($event.detail.value)]!"><view class="health-control">{{ healthTypes[type] }} ▾</view></picker>
    <picker v-if="type === 'BLOOD_GLUCOSE'" :disabled="disabled" :range="scenes.map(s => glucoseScenes[s])" :value="scenes.indexOf(scene)" @change="scene = scenes[Number($event.detail.value)]!"><view class="health-control">{{ glucoseScenes[scene] }} ▾</view></picker>
    <view class="health-actions"><button v-for="n in [7,30,90]" :key="n" :class="{ 'font-button': days === n }" :disabled="disabled" @click="days = n">近{{ n }}天</button></view>
    <button :disabled="disabled" @click="query">查看趋势</button>
    <text class="body-copy">单位：{{ units[type] }}。仅展示实际记录，不作医学判断。</text>
    <text v-if="requested && !points.length && !disabled" class="body-copy">暂无记录。</text>
    <template v-if="points.length">
      <text v-if="type === 'BLOOD_PRESSURE'" class="body-copy">实线：收缩压；虚线：舒张压。两条线对应相同测量点，下面逐点列出两项数值。</text>
      <scroll-view scroll-x><canvas canvas-id="health-trend" id="health-trend" style="width:280px;height:180px" /></scroll-view>
      <text class="body-copy">{{ healthTime(points[0]!.measurement.measuredAt) }} 至 {{ healthTime(points[points.length - 1]!.measurement.measuredAt) }}</text>
      <view v-for="point in values" :key="point.id" class="health-history">
        <text class="body-copy">{{ healthTime(point.measurement.measuredAt) }} · {{ point.entryMode === 'SELF' ? '本人手工录入' : '社区手工代录' }}</text><text class="body-copy">{{ measurementText(point.measurement) }}</text>
      </view>
      <view class="health-actions"><button :disabled="valuePage <= 1" @click="valuePage--">上一页数值</button><text>共{{ points.length }}点</text><button :disabled="valuePage * 20 >= points.length" @click="valuePage++">下一页数值</button></view>
    </template>
  </view>
</template>
