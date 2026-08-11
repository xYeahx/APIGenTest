/**
 * mock-target.js —— 本地模拟被测系统，供执行引擎端到端验证
 * 用法: node mock-target.js [port]   （默认 9090）
 * 路由:
 *   POST /api/login                  -> {code:0, data:{token:"mock-token-xxx"}}
 *   GET  /api/users                  -> 需要 Authorization: Bearer mock-token，返回用户列表
 *   GET  /api/users/:id              -> 需要 token，返回单个用户
 *   GET  /api/orders                 -> 返回订单列表
 *   GET  /api/boom                   -> 固定 500
 *   GET  /api/unauth                 -> 固定 401
 */
const http = require('http')

const port = Number(process.argv[2] || 9090)
let tokenSeq = 0

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify(body))
}

function readBody(req, cb) {
  let raw = ''
  req.on('data', (c) => (raw += c))
  req.on('end', () => {
    try {
      cb(JSON.parse(raw || '{}'))
    } catch (e) {
      cb({})
    }
  })
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`)
  const path = url.pathname
  console.log(`[mock-target] ${req.method} ${path}`)

  if (req.method === 'POST' && path === '/api/login') {
    readBody(req, (body) => {
      if (body.username === 'admin' && body.password === '123456') {
        const token = `mock-token-${++tokenSeq}-${Date.now()}`
        json(res, 200, { code: 0, message: 'ok', data: { token } })
      } else if (body.username === '') {
        json(res, 400, { code: 400, message: '参数不能为空', data: null })
      } else {
        json(res, 401, { code: 401, message: '用户名或密码错误', data: null })
      }
    })
    return
  }

  if (path === '/api/unauth') {
    json(res, 401, { code: 401, message: '未登录', data: null })
    return
  }

  if (path === '/api/boom') {
    json(res, 500, { code: 500, message: '服务器内部错误（模拟）', data: null })
    return
  }

  const auth = req.headers.authorization || ''
  const isAuthed = auth.startsWith('Bearer mock-token-')

  if (path === '/api/users' && req.method === 'GET') {
    if (!isAuthed) {
      json(res, 401, { code: 401, message: '缺少或无效的 token', data: null })
      return
    }
    json(res, 200, {
      code: 0,
      data: [
        { id: 1, username: 'admin', nickname: '管理员' },
        { id: 2, username: 'tester', nickname: '测试员' },
      ],
    })
    return
  }

  const userMatch = path.match(/^\/api\/users\/(\d+)$/)
  if (userMatch && req.method === 'GET') {
    if (!isAuthed) {
      json(res, 401, { code: 401, message: '缺少或无效的 token', data: null })
      return
    }
    json(res, 200, { code: 0, data: { id: Number(userMatch[1]), username: `user-${userMatch[1]}` } })
    return
  }

  if (path === '/api/orders' && req.method === 'GET') {
    json(res, 200, {
      code: 0,
      data: [
        { id: 1001, amount: 99.5, status: 'PAID' },
        { id: 1002, amount: 128.0, status: 'UNPAID' },
      ],
    })
    return
  }

  json(res, 404, { code: 404, message: '接口不存在', data: null })
})

server.listen(port, () => {
  console.log(`mock-target listening on http://127.0.0.1:${port}`)
})