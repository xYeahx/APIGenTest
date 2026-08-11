/**
 * mock-llm.js —— 本地模拟 OpenAI 兼容 Chat Completions 服务
 * 用途：开发阶段无需真实 LLM Key 即可验证 AI 用例生成的完整链路
 *
 * 用法:
 *   node mock-llm.js [mode] [port]
 *   mode: valid(默认,始终输出合法用例) | invalid(始终输出非法内容) | retry(首次非法,重试后合法)
 *   port: 默认 9999
 */
const http = require('http')

const mode = process.argv[2] || 'valid'
const port = Number(process.argv[3] || 9999)
let callCount = 0

function buildContent(apiId) {
  return JSON.stringify({
    results: [
      {
        apiId,
        cases: [
          {
            name: '正常场景-登录成功',
            scenarioType: 'normal',
            method: 'POST',
            urlTemplate: '{{baseUrl}}/api/login',
            headers: { 'Content-Type': 'application/json' },
            body: { username: '{{env:username}}', password: '123456' },
            asserts: [
              { type: 'statusCode', expect: 200 },
              { type: 'field', path: '$.data.token', condition: 'notEmpty' },
            ],
            extractVars: [{ from: 'response', expr: '$.data.token', varName: 'token' }],
          },
          {
            name: '边界场景-空参数',
            scenarioType: 'boundary',
            method: 'POST',
            urlTemplate: '{{baseUrl}}/api/login',
            headers: { 'Content-Type': 'application/json' },
            body: { username: '', password: '' },
            asserts: [{ type: 'statusCode', expect: 400 }],
          },
          {
            name: '异常场景-错误密码',
            scenarioType: 'exception',
            method: 'POST',
            urlTemplate: '{{baseUrl}}/api/login',
            headers: { 'Content-Type': 'application/json' },
            body: { username: '{{env:username}}', password: 'wrong-password' },
            asserts: [{ type: 'statusCode', expect: 401 }],
          },
        ],
      },
    ],
  })
}

http
  .createServer((req, res) => {
    if (req.method !== 'POST' || !req.url.includes('/chat/completions')) {
      res.writeHead(404)
      res.end()
      return
    }
    let raw = ''
    req.on('data', (c) => (raw += c))
    req.on('end', () => {
      callCount++
      let apiId = 1
      try {
        const body = JSON.parse(raw)
        const userMsg = (body.messages || []).find((m) => m.role === 'user')
        const match = (userMsg && userMsg.content || '').match(/接口 ID：(\d+)/)
        if (match) apiId = Number(match[1])
      } catch (e) { /* ignore */ }

      let content
      if (mode === 'invalid') {
        content = '这不是一个合法的 JSON 输出，违反格式要求'
      } else if (mode === 'retry' && callCount === 1) {
        content = '{"results":[]}' // 首次校验不通过，触发重试
      } else {
        content = buildContent(apiId)
      }
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ choices: [{ message: { role: 'assistant', content } }] }))
    })
  })
  .listen(port, () => {
    console.log(`mock-llm listening on http://127.0.0.1:${port} (mode=${mode})`)
  })