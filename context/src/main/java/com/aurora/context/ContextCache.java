package com.aurora.context;

import com.aurora.common.ContextMutationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContextCache {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public ContextCache(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.mapper = mapper;
  }

  public CustomerContext get(String sessionId) {
    try {
      String value = redis.opsForValue().get(key(sessionId));
      return value == null ? null : mapper.readValue(value, CustomerContext.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  public void put(String sessionId, CustomerContext context) {
    try {
      redis
          .opsForValue()
          .set(key(sessionId), mapper.writeValueAsString(context), Duration.ofSeconds(30));
    } catch (Exception ignored) {
      // Postgres remains the source of truth when Redis is unavailable.
    }
  }

  public void evict(String sessionId) {
    try {
      redis.delete(key(sessionId));
    } catch (Exception ignored) {
      // Postgres remains the source of truth when Redis is unavailable.
    }
  }

  @EventListener
  public void evict(ContextMutationEvent event) {
    evict(event.sessionId());
  }

  private String key(String sessionId) {
    return "aurora:context:" + sessionId;
  }
}
