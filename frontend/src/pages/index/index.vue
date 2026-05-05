<template>
  <view class="chat-container">
    <!-- 头部 -->
    <view class="chat-header">
      <text class="header-title">AI 智能助手</text>
      <text class="header-subtitle">Spring AI 驱动</text>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="message-list"
      scroll-y="true"
      :scroll-into-view="scrollToView"
      :scroll-with-animation="true"
    >
      <view
        v-for="(message, index) in messages"
        :key="index"
        :id="'msg-' + index"
        class="message-item"
        :class="message.role === 'user' ? 'message-user' : 'message-ai'"
      >
        <!-- AI头像 -->
        <view v-if="message.role === 'assistant'" class="avatar avatar-ai">
          <text class="avatar-text">AI</text>
        </view>

        <!-- 消息内容 -->
        <view class="message-content">
          <view class="message-bubble">
            <text class="message-text" user-select>{{ message.content }}</text>
          </view>
          <text class="message-time">{{ message.time }}</text>
        </view>

        <!-- 用户头像 -->
        <view v-if="message.role === 'user'" class="avatar avatar-user">
          <text class="avatar-text">我</text>
        </view>
      </view>

      <!-- 加载中 -->
      <view v-if="loading" class="message-item message-ai">
        <view class="avatar avatar-ai">
          <text class="avatar-text">AI</text>
        </view>
        <view class="message-content">
          <view class="message-bubble">
            <view class="loading-dots">
              <view class="dot"></view>
              <view class="dot"></view>
              <view class="dot"></view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 输入区域 -->
    <view class="input-area">
      <view class="input-wrapper">
        <textarea
          class="input-field"
          v-model="inputMessage"
          placeholder="输入你的问题..."
          :auto-height="true"
          :maxlength="2000"
          @confirm="sendMessage"
          :disabled="loading"
        />
        <button
          class="send-button"
          @click="sendMessage"
          :disabled="loading || !inputMessage.trim()"
        >
          发送
        </button>
      </view>
      <view class="input-tips">
        <text class="tips-text">按Enter发送，Shift+Enter换行</text>
      </view>
    </view>
  </view>
</template>

<script>
import { chatAPI } from '@/utils/api.js'

export default {
  data() {
    return {
      messages: [
        {
          role: 'assistant',
          content: '你好！我是AI智能助手，有什么可以帮助你的吗？',
          time: this.formatTime(new Date())
        }
      ],
      inputMessage: '',
      loading: false,
      scrollToView: ''
    }
  },
  methods: {
    async sendMessage() {
      if (!this.inputMessage.trim() || this.loading) return

      // 添加用户消息
      const userMessage = {
        role: 'user',
        content: this.inputMessage,
        time: this.formatTime(new Date())
      }
      this.messages.push(userMessage)

      // 清空输入框
      const question = this.inputMessage
      this.inputMessage = ''

      // 滚动到底部
      this.scrollToBottom()

      // 设置加载状态
      this.loading = true

      try {
        // 调用API
        const response = await chatAPI.sendMessage(question)

        // 添加AI回复
        this.messages.push({
          role: 'assistant',
          content: response.content || '抱歉，我没有收到回复',
          time: this.formatTime(new Date())
        })
      } catch (error) {
        console.error('发送消息失败:', error)
        uni.showToast({
          title: '发送失败，请重试',
          icon: 'none'
        })

        // 添加错误提示
        this.messages.push({
          role: 'assistant',
          content: '抱歉，服务暂时不可用，请稍后重试。',
          time: this.formatTime(new Date())
        })
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const lastIndex = this.messages.length - 1
        this.scrollToView = 'msg-' + lastIndex
      })
    },

    formatTime(date) {
      const hours = date.getHours().toString().padStart(2, '0')
      const minutes = date.getMinutes().toString().padStart(2, '0')
      return `${hours}:${minutes}`
    }
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #F5F5F5;
}

/* 头部样式 */
.chat-header {
  background: linear-gradient(135deg, #4A90E2, #357ABD);
  padding: 20rpx 30rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.1);
}

.header-title {
  font-size: 36rpx;
  font-weight: bold;
  color: #FFFFFF;
  display: block;
}

.header-subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
  display: block;
}

/* 消息列表 */
.message-list {
  flex: 1;
  padding: 20rpx;
  overflow-y: auto;
}

.message-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 30rpx;
}

.message-user {
  flex-direction: row-reverse;
}

/* 头像 */
.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-ai {
  background: linear-gradient(135deg, #4A90E2, #357ABD);
}

.avatar-user {
  background: linear-gradient(135deg, #50C878, #3AAF6F);
}

.avatar-text {
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: bold;
}

/* 消息内容 */
.message-content {
  max-width: 70%;
  margin: 0 20rpx;
}

.message-user .message-content {
  align-items: flex-end;
}

.message-bubble {
  background-color: #FFFFFF;
  padding: 20rpx 30rpx;
  border-radius: 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.05);
  word-break: break-all;
}

.message-user .message-bubble {
  background: linear-gradient(135deg, #4A90E2, #357ABD);
}

.message-text {
  font-size: 30rpx;
  line-height: 1.6;
  color: #333333;
}

.message-user .message-text {
  color: #FFFFFF;
}

.message-time {
  font-size: 22rpx;
  color: #999999;
  margin-top: 8rpx;
  display: block;
}

/* 加载动画 */
.loading-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 0;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: #999999;
  animation: bounce 1.4s infinite ease-in-out;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

/* 输入区域 */
.input-area {
  background-color: #FFFFFF;
  padding: 20rpx;
  box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 20rpx;
}

.input-field {
  flex: 1;
  background-color: #F5F5F5;
  border-radius: 20rpx;
  padding: 20rpx;
  font-size: 30rpx;
  min-height: 80rpx;
  max-height: 200rpx;
}

.send-button {
  background: linear-gradient(135deg, #4A90E2, #357ABD);
  color: #FFFFFF;
  border: none;
  border-radius: 20rpx;
  padding: 0 40rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 30rpx;
}

.send-button:disabled {
  background: #CCCCCC;
}

.input-tips {
  margin-top: 10rpx;
  text-align: center;
}

.tips-text {
  font-size: 22rpx;
  color: #999999;
}
</style>
