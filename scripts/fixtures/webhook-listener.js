const http = require('http')
const fs = require('fs')
const port = Number(process.argv[2] || 9091)
const file = process.argv[3] || 'webhook-captured.txt'
const server = http.createServer((req, res) => {
  let raw = ''
  req.on('data', (c) => (raw += c))
  req.on('end', () => {
    try {
      fs.appendFileSync(file, raw + '\n')
    } catch (e) { /* ignore */ }
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end('{"ok":true}')
  })
})
server.listen(port, () => console.log('webhook listener on ' + port))