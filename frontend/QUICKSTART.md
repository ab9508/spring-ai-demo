# 快速启动指南

## 项目说明

这是一个基于uni-app的AI聊天前端项目，对接Spring AI后端服务。

## 启动步骤

### 1. 启动后端服务

```bash
cd spring-ai-demo
mvn spring-boot:run
```

后端启动后，访问：<INTERNAL_HOST_REMOVED>
- 测试接口1：<INTERNAL_HOST_REMOVED>
- 测试接口2：<INTERNAL_HOST_REMOVED>

### 2. 安装前端依赖

```bash
cd spring-ai-demo/frontend
npm install
```

### 3. 启动前端（H5端）

```bash
npm run dev:h5
```

启动后访问：<INTERNAL_HOST_REMOVED>

### 4. 测试对话

在前端页面输入消息，应该能收到AI回复。

## 常见问题

### Q1: 前端无法连接后端？

**检查清单：**
- [ ] 后端是否已启动（访问 <INTERNAL_HOST_REMOVED> 测试）
- [ ] 后端是否配置了CORS（ChatController已添加@CrossOrigin）
- [ ] 前端API地址是否正确（src/utils/api.js中的BASE_URL）

**解决方法：**
如果后端端口不是8080，修改 `frontend/src/utils/api.js`：
```javascript
const BASE_URL = '<INTERNAL_HOST_REMOVED>  // 修改为实际端口
```

### Q2: 后端编译失败？

```bash
cd spring-ai-demo
mvn clean compile
```

查看错误信息，可能是：
- 缺少依赖
- 语法错误
- 导入缺失

### Q3: 前端页面空白？

打开浏览器控制台（F12），查看错误信息：
- 如果提示"跨域错误"，检查后端CORS配置
- 如果提示"网络错误"，检查后端是否启动

## 项目结构

```
spring-ai-demo/
├── frontend/              # 前端项目
│   ├── src/
│   │   ├── pages/
│   │   │   └── index/
│   │   │       └── index.vue    # 主聊天页面
│   │   ├── utils/
│   │   │   └── api.js          # API调用工具
│   │   ├── App.vue
│   │   ├── main.js
│   │   └── pages.json
│   ├── package.json
│   └── README.md
└── src/                   # 后端项目
    └── main/java/com/example/ai/controller/
        └── ChatController.java  # 已添加/api/chat接口
```

## 下一步

1. **测试基本对话功能**
2. **添加Token用量显示**
3. **实现流式输出（SSE）**
4. **添加对话历史功能**
5. **部署到生产环境**

## 技术栈

- **前端**: uni-app + Vue 3 + Vite
- **后端**: Spring Boot 3.4.5 + Spring AI 1.0.5
- **AI模型**: DeepSeek（通过OpenAI兼容接口）

## 联系方式

如有问题，请检查：
1. 后端日志：查看控制台输出
2. 前端日志：浏览器控制台（F12）
3. 网络请求：浏览器Network标签
