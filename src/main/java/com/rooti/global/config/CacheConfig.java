package com.rooti.global.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 기반 캐시 매니저.
 *
 * <p>{@code @EnableCaching} 으로 Spring 의 {@code @Cacheable} / {@code @CacheEvict} 어노테이션을
 * 활성화합니다. RDS 비용 최적화 관점에서, "거의 안 바뀌는데 자주 조회되는" 도메인(회사 / 업무 표준 /
 * 사용자 기본 정보) 는 캐시 적중률이 90%+ 가 나옵니다. 캐시 적중 = RDS read 0건 = IOPS·CPU 0
 * → 같은 트래픽이면 더 작은 RDS 클래스로 충분.
 *
 * <p>캐시별 TTL 은 데이터 변동성에 맞춰 분리:
 *
 * <ul>
 *   <li>{@code companies} — 회사 정보는 거의 안 바뀜. 10분.
 *   <li>{@code job-standards} — 업무 표준도 거의 안 바뀜. 10분.
 *   <li>{@code users} — 회원 정보 5분 (이메일/이름 변경 빈도 낮음).
 * </ul>
 *
 * <p>도메인 변경 시점에 {@code @CacheEvict} 로 명시적 무효화 — TTL 에만 의존하지 않습니다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMPANIES = "companies";
    public static final String JOB_STANDARDS = "job-standards";
    public static final String USERS = "users";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
                        .activateDefaultTyping(
                                BasicPolymorphicTypeValidator.builder()
                                        .allowIfBaseType(Object.class)
                                        .build(),
                                ObjectMapper.DefaultTyping.NON_FINAL);
        var jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer))
                        .computePrefixWith(name -> "rooti:cache:" + name + ":")
                        .disableCachingNullValues()
                        .entryTtl(Duration.ofMinutes(5));

        Map<String, RedisCacheConfiguration> perCache =
                Map.of(
                        COMPANIES, base.entryTtl(Duration.ofMinutes(10)),
                        JOB_STANDARDS, base.entryTtl(Duration.ofMinutes(10)),
                        USERS, base.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }
}
