#!/usr/bin/env node
/**
 * 零依赖静态文件服务 + /api 反向代理
 * 用途：托管 frontend/dist，并把 API 请求转发到本机 Spring Boot 后端。
 * 端口与后端地址由环境变量控制：PORT / API_TARGET / DIST_DIR
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 8089);
const HOST = process.env.HOST || '0.0.0.0';
const API_TARGET = process.env.API_TARGET || 'http://127.0.0.1:8088';
const DIST_DIR = path.resolve(process.env.DIST_DIR || path.join(__dirname, '..', 'frontend', 'dist'));
const PROXY_PREFIXES = ['/api', '/v3', '/swagger-ui', '/actuator'];

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

const target = new URL(API_TARGET);

function proxy(req, res) {
  const options = {
    hostname: target.hostname,
    port: target.port || 80,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: target.host },
  };
  const upstream = http.request(options, (up) => {
    res.writeHead(up.statusCode || 502, up.headers);
    up.pipe(res);
  });
  upstream.on('error', (err) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' });
    }
    res.end(JSON.stringify({ success: false, message: '后端不可用: ' + err.message }));
  });
  req.pipe(upstream);
}

function sendFile(res, filePath, statusCode) {
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(500, { 'content-type': 'text/plain; charset=utf-8' });
      return res.end('读取文件失败');
    }
    res.writeHead(statusCode || 200, {
      'content-type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      'cache-control': path.basename(filePath) === 'index.html' ? 'no-cache' : 'public, max-age=3600',
    });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);

  if (PROXY_PREFIXES.some((p) => urlPath === p || urlPath.startsWith(p + '/'))) {
    return proxy(req, res);
  }

  // 防目录穿越
  const candidate = path.resolve(DIST_DIR, '.' + urlPath);
  if (!candidate.startsWith(DIST_DIR)) {
    res.writeHead(403, { 'content-type': 'text/plain; charset=utf-8' });
    return res.end('Forbidden');
  }

  fs.stat(candidate, (err, stat) => {
    if (!err && stat.isFile()) {
      return sendFile(res, candidate);
    }
    // SPA 回退：非静态资源一律交回 index.html 给 react-router
    sendFile(res, path.join(DIST_DIR, 'index.html'));
  });
});

server.listen(PORT, HOST, () => {
  console.log(`[static-server] http://${HOST}:${PORT}  dist=${DIST_DIR}  proxy -> ${API_TARGET}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
