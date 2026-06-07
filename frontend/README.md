# Spring AI Chat 前端

基于 uni-app 的 AI 聊天界面，支持多端发布（H5、微信小程序、App等）。

## 项目结构

```
frontend/
├── src/
│   ├── pages/
│   │   └── index/
│   │       └── index.vue          # 主聊天页面
│   ├── components/                # 组件目录
│   ├── utils/
│   │   └── api.js                 # API调用工具
│   ├── static/                    # 静态资源
│   ├── App.vue                    # 应用入口
│   ├── main.js                    # 应用配置
│   └── pages.json                 # 页面路由
├── package.json                   # 项目依赖
└── README.md                     # 项目说明
```

## 快速开始

### 1. 安装依赖

```bash
cd spring-ai-demo/frontend
npm install
```

### 2. 配置后端API地址

编辑 `src/utils/api.js` 文件，修改 `BASE_URL`：

```javascript
// 本地开发
const BASE_URL = 'http://localhost:8080'

// 如果后端端口不是8080，请修改为实际端口
```

### 3. 启动后端服务

确保Spring AI后端服务已启动，默认地址：<ADDRESS_REDACTED>

### 4. 运行前端

#### H5端（推荐用于PC端开发测试）

```bash
npm run dev:h5
```

访问：`<INTERNAL_HOST_REMOVED>

#### 微信小程序端

```bash
# 需要先安装微信开发者工具
npm run dev:mp-weixin
```

然后使用微信开发者工具打开 `dist/dev/mp-weixin` 目录。

## 后端API接口要求

前端需要后端提供以下API接口：

### 1. 发送聊天消息

- **接口**: `POST /api/chat`
- **请求体**:
  ```json
  {
    "message": "用户消息",
    "conversationId": "default",
    "stream": false
  }
  ```
- **响应**:
  ```json
  {
    "content": "AI回复内容",
    "conversationId": "default"
  }
  ```

### 2. 清空对话历史

- **接口**: `POST /api/chat/clear`
- **请求体**:
  ```json
  {
    "conversationId": "default"
  }
  ```

## 功能特性

- ✅ AI对话界面
- ✅ 消息历史展示
- ✅ 加载状态提示
- ✅ 自动滚动到底部
- ✅ 时间戳显示
- ✅ 响应式设计（适配PC和移动端）
- 🚧 流式输出（待实现）
- 🚧 Token用量显示（待实现）

## 下一步计划

1. **对接后端API**: 根据实际后端接口调整API调用
2. **添加流式输出**: 使用SSE或WebSocket实现流式回复
3. **Token监控显示**: 显示每次对话的Token消耗量
4. **对话历史管理**: 支持多个对话会话
5. **文件上传**: 支持图片/文件上传功能
6. **Markdown渲染**: 支持AI回复的Markdown格式渲染

## 技术栈

- [uni-app](https://uniapp.dcloud.net.cn/) - 跨平台应用框架
- [Vue 3](https://vuejs.org/) - 渐进式JavaScript框架
- [Vite](https://vitejs.dev/) - 现代前端构建工具

## 注意事项

1. **跨域问题**: 本地开发时，需要后端配置CORS支持
2. **PC端优化**: 当前界面已针对PC端优化，使用较大的间距和字体
3. **本地运行**: 当前配置为本地运行，部署时需要修改API地址

## 常见问题

### Q: 前端无法连接后端？

A: 检查以下问题：
1. 后端服务是否已启动（<INTERNAL_HOST_REMOVED>
2. 后端是否配置了CORS允许跨域
3. API地址配置是否正确

### Q: 如何修改后端端口？

A: 修改 `src/utils/api.js` 中的 `BASE_URL`，将8080修改为实际端口。

### Q: 如何打包部署？

A: 运行构建命令：
```bash
# H5端
npm install --legacy-peer-deps
npm run build:h5

C:\Users\ASUS\WorkBuddy\20260425094145\spring-ai-demo\frontend\chat.html
# 生成的文件在 dist/build/h5 目录
```
