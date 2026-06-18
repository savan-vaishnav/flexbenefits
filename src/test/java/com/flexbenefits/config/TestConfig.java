package com.flexbenefits.config;

import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Test configuration that provides mock beans for external services
 * (MinIO, Redis) so unit/integration tests don't need Docker running.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public MinioClient testMinioClient() {
        return Mockito.mock(MinioClient.class);
    }

    @Bean
    @Primary
    public RedisConnectionFactory testRedisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate testStringRedisTemplate() {
        StringRedisTemplate mock = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(mock.opsForValue()).thenReturn(valueOps);
        return mock;
    }
}
