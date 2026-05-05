// API配置
const BASE_URL = 'http://localhost:8080'

// 聊天API
export const chatAPI = {
  /**
   * 发送消息到AI
   * @param {string} message - 用户消息
   * @param {string} conversationId - 会话ID（可选）
   * @returns {Promise<Object>} - AI回复
   */
  async sendMessage(message, conversationId = 'default') {
    try {
      const response = await uni.request({
        url: `${BASE_URL}/api/chat`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json'
        },
        data: {
          message: message,
          conversationId: conversationId,
          stream: false // 非流式返回
        }
      })

      if (response.statusCode === 200) {
        return {
          content: response.data.content || response.data.message || '抱歉，我没有收到回复',
          conversationId: response.data.conversationId || conversationId
        }
      } else {
        throw new Error(`请求失败: ${response.statusCode}`)
      }
    } catch (error) {
      console.error('API调用失败:', error)
      throw error
    }
  },

  /**
   * 流式聊天（可选实现）
   * @param {string} message - 用户消息
   * @param {Function} onChunk - 接收每个chunk的回调
   * @param {string} conversationId - 会话ID
   */
  async sendMessageStream(message, onChunk, conversationId = 'default') {
    // 注意：uniapp的uni.request不支持stream，需要使用uni.connectSocket或其他方式
    // 这里提供一个简单的轮询实现作为示例
    console.warn('流式输出在uniapp中需要特殊处理，当前使用普通模式')
    return this.sendMessage(message, conversationId)
  },

  /**
   * 清空对话历史
   * @param {string} conversationId - 会话ID
   */
  async clearHistory(conversationId = 'default') {
    try {
      const response = await uni.request({
        url: `${BASE_URL}/api/chat/clear`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json'
        },
        data: {
          conversationId: conversationId
        }
      })

      return response.statusCode === 200
    } catch (error) {
      console.error('清空历史失败:', error)
      return false
    }
  }
}

// 通用请求工具
export const request = {
  /**
   * GET请求
   * @param {string} url - 请求路径
   * @param {Object} params - 查询参数
   */
  get(url, params = {}) {
    return uni.request({
      url: `${BASE_URL}${url}`,
      method: 'GET',
      data: params
    })
  },

  /**
   * POST请求
   * @param {string} url - 请求路径
   * @param {Object} data - 请求体
   */
  post(url, data = {}) {
    return uni.request({
      url: `${BASE_URL}${url}`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json'
      },
      data: data
    })
  }
}

export default {
  chatAPI,
  request
}
