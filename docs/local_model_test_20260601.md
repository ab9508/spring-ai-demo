# Local Model Test Results
## Date: 2026-06-01
## Chat: qwen2.5:3b | Embedding: bge-large-zh-v1.5 | Vector: PgVector 1024d

### RAG Tests

| Query | Result |
|-------|--------|
| 退货流程 | PASS - 5-step process + 7-day return |
| 包邮条件 | PASS - Free shipping over 99 yuan |
| 今天天气 | PASS - Refused, no knowledge |
| 积分怎么用 | PASS - 100 points = 1 yuan |

### Agent Tests

| Request | Result |
|---------|--------|
| 查一下订单ORD-001 | PASS - Tool calling works |
| 查物流ORD-001 | PASS - Logistics tool called |

### Observations
- qwen2.5:3b supports tool calling correctly
- BGE embedding distinguishes relevant (0.62+) vs irrelevant (0.38) queries
- RAG system responds accurately from FAQ content
- Model runs locally, no API dependency
