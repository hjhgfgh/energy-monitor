import axios from 'axios'

const http = axios.create({ baseURL: '', timeout: 8000 })

// 请求拦截：自动附带 JWT
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('em_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一处理业务码与过期
http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body.code === 0) return body.data
    if (body.code === 4010 || body.code === 4001) {
      localStorage.removeItem('em_token')
      location.hash = '#/login'
    }
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('em_token')
      location.hash = '#/login'
    }
    return Promise.reject(err)
  }
)

export const login = (username, password) =>
  http.post('/api/auth/login', { username, password })

export const listDevices = () => http.get('/api/devices')

export const latestData = () => http.get('/api/devices/latest-data')

export const recentData = (deviceId, hours = 1, limit = 300) =>
  http.get(`/api/devices/${deviceId}/recent`, { params: { hours, limit } })

export const listAlarms = (page = 1, size = 8) =>
  http.get('/api/alarms', { params: { page, size } })

/** 建立实时推送连接。浏览器 WS 无法自定义 header，token 走查询参数 */
export function openRealtimeSocket(onMessage) {
  const token = localStorage.getItem('em_token')
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const ws = new WebSocket(`${proto}://${location.host}/ws/realtime?token=${token}`)
  ws.onmessage = (e) => { try { onMessage(JSON.parse(e.data)) } catch { /* 忽略非 JSON 帧 */ } }
  // 断线 3 秒后自动重连（简单退避，工程上可加指数退避）
  ws.onclose = () => setTimeout(() => openRealtimeSocket(onMessage), 3000)
  return ws
}
