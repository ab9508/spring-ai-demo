package com.example.ai.controller;


import jakarta.annotation.Resource;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ab
 * @date 2026/4/29
 **/
@RestController
@Profile("!mcp-server")   // 加在这
@RequestMapping("/test/redis")
public class RedisController {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/get")
    public String get(){
        stringRedisTemplate.opsForValue().set("k1","v1");
        return stringRedisTemplate.opsForValue().get("k1");
    }

}
