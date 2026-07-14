package com.rishavdev.UrlShortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RedisTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testRedisConnection() {
        String testKey = "after_config";
        String testValue = "workingFine";

        redisTemplate.opsForValue().set(testKey, testValue);
        String result = redisTemplate.opsForValue().get(testKey);

        assertThat(result).isEqualTo(testValue);
    }
}

