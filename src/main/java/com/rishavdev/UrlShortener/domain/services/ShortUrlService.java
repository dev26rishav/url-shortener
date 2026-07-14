package com.rishavdev.UrlShortener.domain.services;

import com.rishavdev.UrlShortener.ApplicationProperties;
import com.rishavdev.UrlShortener.domain.entities.ShortUrl;
import com.rishavdev.UrlShortener.domain.models.CreateShortUrlCmd;
import com.rishavdev.UrlShortener.domain.models.PagedResult;
import com.rishavdev.UrlShortener.domain.models.ShortUrlDto;
import com.rishavdev.UrlShortener.domain.repositories.ShortUrlRepository;
import com.rishavdev.UrlShortener.domain.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final EntityMapper entityMapper;
    private final ApplicationProperties properties;
    private final UserRepository userRepository;
    private final RedisService redisService;

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           EntityMapper entityMapper,
                           ApplicationProperties properties, UserRepository userRepository, RedisService redisService) {
        this.shortUrlRepository = shortUrlRepository;
        this.entityMapper = entityMapper;
        this.properties = properties;
        this.userRepository = userRepository;
        this.redisService = redisService;
    }

    private Pageable getPageable(int page, int size) {
        page = page > 1 ? page - 1: 0;
        return PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
    }

    public PagedResult<ShortUrlDto> findAllPublicShortUrls(int pageNo, int pageSize) {
        Pageable pageable = getPageable(pageNo, pageSize);
        Page<ShortUrlDto> shortUrlDtoPage = shortUrlRepository.findPublicShortUrls(pageable)
                .map(entityMapper::toShortUrlDto);
        return PagedResult.from(shortUrlDtoPage);
    }

    public PagedResult<ShortUrlDto> findAllShortUrls(int pageNo, int pageSize) {
        Pageable pageable = getPageable(pageNo, pageSize);
        Page<ShortUrlDto> allShortUrlsDto = shortUrlRepository.findAllShortUrls(pageable).map(entityMapper::toShortUrlDto);
        return PagedResult.from(allShortUrlsDto);
    }

    public PagedResult<ShortUrlDto> getUserShortUrls(Long userId, int page, int pageSize) {
        Pageable pageable =  getPageable(page, pageSize);
        Page<ShortUrlDto> userShortUrls = shortUrlRepository.findByCreatedById(userId, pageable).map(entityMapper::toShortUrlDto);
        return PagedResult.from(userShortUrls);
    }

    @Transactional
    public ShortUrlDto createShortUrl(CreateShortUrlCmd cmd) {
        if(properties.validateOriginalUrl()) {
            boolean urlExists = UrlExistenceValidator.isUrlExists(cmd.originalUrl());
            if(!urlExists) {
                throw new RuntimeException("Invalid URL "+cmd.originalUrl());
            }
        }
        var shortKey = generateUniqueShortKey();
        var shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(cmd.originalUrl());
        shortUrl.setShortKey(shortKey);
        if(cmd.userId() == null) {
            shortUrl.setCreatedBy(null);
            shortUrl.setIsPrivate(false);
            shortUrl.setExpiresAt(Instant.now().plus(properties.defaultExpiryInDays(), DAYS));
        } else {
            shortUrl.setCreatedBy(userRepository.findById(cmd.userId()).orElseThrow());
            shortUrl.setIsPrivate(cmd.isPrivate() != null && cmd.isPrivate());
            shortUrl.setExpiresAt(cmd.expirationInDays() != null ? Instant.now().plus(cmd.expirationInDays(), DAYS) : null);
        }
        shortUrl.setClickCount(0L);
        shortUrl.setCreatedAt(Instant.now());
        shortUrlRepository.save(shortUrl);
        return entityMapper.toShortUrlDto(shortUrl);
    }

    private String generateUniqueShortKey() {
        String shortKey;
        do {
            shortKey = generateRandomShortKey();
        } while (shortUrlRepository.existsByShortKey(shortKey));
        return shortKey;
    }

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_KEY_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomShortKey() {
        StringBuilder sb = new StringBuilder(SHORT_KEY_LENGTH);
        for (int i = 0; i < SHORT_KEY_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private boolean canUserAccessUrl(ShortUrlDto urlToCheck, Long userId) {
        return urlToCheck.isPrivate() != null && urlToCheck.createdBy() != null && urlToCheck.isPrivate()
                && !Objects.equals(urlToCheck.createdBy().id(), userId);
    }

    @Transactional
    public Optional<ShortUrlDto> accessShortUrl(String shortKey, Long userId) {
        // check in redis
        ShortUrlDto cachedShortUrl = redisService.get(shortKey);

        if (cachedShortUrl != null) {
            if(cachedShortUrl.expiresAt() != null && cachedShortUrl.expiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (canUserAccessUrl(cachedShortUrl, userId)) {
                return Optional.empty();
            }
            return Optional.of(cachedShortUrl);
        } else {
            Optional<ShortUrl> shortUrlOptional = shortUrlRepository.findByShortKey(shortKey);
            if(shortUrlOptional.isEmpty()) {
                return Optional.empty();
            }
            ShortUrl shortUrl = shortUrlOptional.get();
            if(shortUrl.getExpiresAt() != null && shortUrl.getExpiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if(canUserAccessUrl(entityMapper.toShortUrlDto(shortUrl), userId)) {
                return Optional.empty();
            }
            shortUrl.setClickCount(shortUrl.getClickCount()+1);
            shortUrlRepository.save(shortUrl);
            redisService.set(shortUrl.getShortKey(), shortUrl);
            return shortUrlOptional.map(entityMapper::toShortUrlDto);
        }
    }

    @Transactional
    public void deleteUserShortUrls(List<Long> ids, Long userId) {
        if (ids != null && !ids.isEmpty() && userId != null) {
            shortUrlRepository.deleteByIdInAndCreatedById(ids, userId);
        }
    }
}
