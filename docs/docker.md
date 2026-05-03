
# pgvector部署

```
 docker run -d \
  --name postgres \
  --restart always \
  -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=mydb \
  -v /home/ab/postgres/data:/var/lib/postgresql/data \
  pgvector/pgvector:pg14
```

# redis 部署
```
docker run -d \
  --name redis \
  --restart always \
  -p 6379:6379 \
  -v /home/ab/redis/data:/data \
  redis:latest redis-server --appendonly yes
```

# 对应yml配置
```
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
```

# 常用命令

查看日志
docker logs postgres
docker logs redis

重启服务
docker restart postgres redis

关闭
docker stop postgres redis