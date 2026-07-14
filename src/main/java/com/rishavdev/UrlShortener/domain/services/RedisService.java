package com.rishavdev.UrlShortener.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishavdev.UrlShortener.domain.entities.ShortUrl;
import com.rishavdev.UrlShortener.domain.models.ShortUrlDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RedisService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityMapper entityMapper;

    public ShortUrlDto get(String key) {
        try {
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, ShortUrlDto.class);
            }
            return null;
        } catch (Exception e) {
            log.error("Exception while fetching: {}", e.getMessage(), e);
            return null;
        }
    }

    public void set(String key, ShortUrl shortUrl) {
        try {
            ShortUrlDto shortUrlDto = entityMapper.toShortUrlDto(shortUrl);
            String jsonString = objectMapper.writeValueAsString(shortUrlDto);
            redisTemplate.opsForValue().set(key, jsonString);
        } catch (Exception e) {
            log.error("Exception while setting: {}", e.getMessage(), e);
        }
    }
}

