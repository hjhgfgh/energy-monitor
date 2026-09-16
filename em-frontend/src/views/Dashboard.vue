<template>
  <div class="dashboard">
    <header class="hd">
      <span class="hd-title">⚡ 智慧能源监控大屏</span>
      <span class="hd-right">
        <el-tag :type="wsOk ? 'success' : 'danger'" effect="dark" size="small">
          {{ wsOk ? '实时推送已连接' : '推送未连接' }}
        </el-tag>
        <span class="user">{{ username }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </span>
    </header>

    <!-- 设备状态卡片 -->
    <section class="cards">
      <el-card v-for="d in devices" :key="d.deviceId" class="card" shadow="hover">
        <div class="card-name">{{ d.deviceName }}</div>
        <div class="card-row"><span>电压</span><b :class="vClass(d.voltage)">{{ fmt(d.voltage) }} V</b></div>
        <div class="card-row"><span>电流</span><b>{{ fmt(d.electricCurrent) }} A</b></div>
        <div class="card-row"><span>功率</span><b>{{ fmt(d.power) }} W</b></div>
        <div class="card-time">更新于 {{ d.time || '—' }}</div>
      </el-card>
      <el-empty v-if="!devices.length" description="暂无设备数据，请启动模拟器" class="empty" />
    </section>

    <!-- 实时曲线 -->
    <section class="charts">
      <el-card class="chart-card" shadow="never">
        <template #header>电压实时曲线（V）</template>
        <div ref="voltageEl" class="chart"></div>
      </el-card>
      <el-card class="chart-card" shadow="never">
        <template #header>功率实时曲线（W）</template>
        <div ref="powerEl" class="chart"></div>
      </el-card>
    </section>

    <!-- 告警列表 -->
    <section class="alarms">
      <el-card shadow="never">
        <template #header>最新告警</template>
        <el-table :data="alarms" size="small" stripe>
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column prop="deviceId" label="设备" width="90" />
          <el-table-column prop="level" label="级别" width="80">
            <template #default="{ row }">
              <el-tag :type="row.level === 1 ? 'danger' : 'warning'" size="small">
                {{ { 1: '严重', 2: '警告', 3: '提示' }[row.level] || row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="内容" />
        </el-table>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import { listDevices, latestData, recentData, listAlarms, openRealtimeSocket } from '../api'

const router = useRouter()
const username = localStorage.getItem('em_user') || 'admin'

const devices = ref([])          // 卡片数据 {deviceId, deviceName, voltage, electricCurrent, power, time}
const alarms = ref([])
const wsOk = ref(false)
const voltageEl = ref(null)      // ECharts 容器
const powerEl = ref(null)

const MAX_POINTS = 120           // 曲线最多保留 120 个点，超出丢弃最旧的

// series 三个：device 1001/1002/1003
const seriesMeta = [
  { id: 1001, name: '1号车间总表', color: '#5470c6' },
  { id: 1002, name: '2号车间总表', color: '#91cc75' },
  { id: 1003, name: '光伏逆变器-01', color: '#fac858' }
]
const voltageData = Object.fromEntries(seriesMeta.map(m => [m.id, []]))
const powerData = Object.fromEntries(seriesMeta.map(m => [m.id, []]))

let vChart, pChart, socket

const fmt = (n) => (n == null ? '--' : Number(n).toFixed(2))
const vClass = (v) => (v != null && v > 240 ? 'over' : '')

function baseOption(title) {
  return {
    animation: false,
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 50, right: 16, top: 30, bottom: 24 },
    xAxis: { type: 'category', data: [] },
    yAxis: { type: 'value', name: title },
    series: seriesMeta.map(m => ({
      name: m.name, type: 'line', showSymbol: false, smooth: true,
      itemStyle: { color: m.color }, lineStyle: { width: 1.5, color: m.color },
      data: []
    }))
  }
}

function pushPoint(store, ts) {
  for (const m of seriesMeta) {
    const arr = store[m.id]
    arr.push(ts[m.id] ?? null)
    if (arr.length > MAX_POINTS) arr.shift()
  }
}

function renderChart(chart, store) {
  if (!chart) return
  chart.setOption({
    xAxis: { data: store[seriesMeta[0].id].map((_, i) => i + 1) },
    series: seriesMeta.map(m => ({ data: store[m.id] }))
  })
}

async function loadInit() {
  // 设备卡片初值 + 历史曲线回填 + 告警列表
  try {
    const [latest, alarmsPage] = await Promise.all([latestData(), listAlarms()])
    devices.value = (latest || []).map(x => ({
      deviceId: x.deviceId, deviceName: x.deviceName || `设备 ${x.deviceId}`,
      voltage: x.voltage, electricCurrent: x.electricCurrent, power: x.power,
      time: fmtTime(x.collectTime)
    }))
    alarms.value = (alarmsPage?.records || []).map(a => ({ ...a, createdAt: fmtTime(a.createdAt) }))

    for (const m of seriesMeta) {
      try {
        const rows = await recentData(m.id, 1, MAX_POINTS)
        voltageData[m.id] = (rows || []).map(r => r.voltage)
        powerData[m.id] = (rows || []).map(r => r.power)
      } catch { /* 设备无数据时跳过 */ }
    }
    renderChart(vChart, voltageData)
    renderChart(pChart, powerData)
  } catch (e) { /* 初次加载失败不打断 WS 流程 */ }
}

/** epoch 毫秒 / ISO 字符串 → HH:mm:ss */
const fmtTime = (v) => {
  if (!v) return '--'
  const d = new Date(v)
  if (isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function handlePush(msg) {
  // 推送格式见 em-data-process RealtimeMessage：{type:'data'|'alarm', data:{...}}
  if (msg.type === 'alarm') {
    const a = msg.data
    alarms.value.unshift({
      createdAt: fmtTime(a.createdAt) || '--',
      deviceId: a.deviceId, level: a.level, content: a.content
    })
    if (alarms.value.length > 20) alarms.value.pop()
    return
  }
  if (msg.type !== 'data') return
  wsOk.value = true
  const d = msg.data
  const id = d.deviceId
  const card = devices.value.find(x => x.deviceId === id)
  if (card) {
    card.voltage = d.voltage; card.electricCurrent = d.electricCurrent
    card.power = d.power; card.time = fmtTime(d.collectTime)
  } else {
    devices.value.push({
      deviceId: id, deviceName: `设备 ${id}`, voltage: d.voltage,
      electricCurrent: d.electricCurrent, power: d.power,
      time: fmtTime(d.collectTime)
    })
  }
  pushPoint(voltageData, { [id]: d.voltage })
  pushPoint(powerData, { [id]: d.power })
  renderChart(vChart, voltageData)
  renderChart(pChart, powerData)
}

function logout() {
  localStorage.removeItem('em_token')
  router.push('/login')
}

onMounted(async () => {
  vChart = echarts.init(voltageEl.value)
  pChart = echarts.init(powerEl.value)
  vChart.setOption(baseOption('V'))
  pChart.setOption(baseOption('W'))
  await loadInit()
  socket = openRealtimeSocket(handlePush)
})

onUnmounted(() => { socket?.close(); vChart?.dispose(); pChart?.dispose() })
</script>

<style scoped>
.dashboard { min-height: 100%; background: #0e1a2b; color: #dfe8f2; padding: 12px 16px; }
.hd { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.hd-title { font-size: 22px; font-weight: 600; letter-spacing: 2px; }
.hd-right { display: flex; align-items: center; gap: 10px; }
.user { opacity: .8; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; margin-bottom: 12px; }
.card :deep(.el-card__body) { padding: 12px 16px; }
.card-name { font-weight: 600; margin-bottom: 8px; }
.card-row { display: flex; justify-content: space-between; font-size: 13px; margin: 4px 0; }
.card-row b.over { color: #f56c6c; }
.card-time { font-size: 11px; opacity: .5; margin-top: 6px; }
.empty { grid-column: 1 / -1; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.chart { height: 260px; }
@media (max-width: 900px) { .charts { grid-template-columns: 1fr; } }
</style>
