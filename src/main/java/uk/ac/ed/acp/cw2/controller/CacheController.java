package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import uk.ac.ed.acp.cw2.data.RuntimeEnvironment;

/**
 * Controller class responsible for handling REST endpoints for managing
 * cache storage using Redis. Provides functionality to retrieve and store
 * key-value pairs in the cache.
 */
@RestController()
@RequestMapping("/cache")
public class CacheController {

    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);
    private final RuntimeEnvironment environment;

    public CacheController(RuntimeEnvironment environment) {
        this.environment = environment;
    }

    @GetMapping("/{cacheKey}")
    public String retrieveFromCache(@PathVariable String cacheKey) {
        logger.info(String.format("Retrieving %s from cache", cacheKey));
        try (JedisPool pool = new JedisPool(environment.getRedisHost(), environment.getRedisPort()); Jedis jedis = pool.getResource()) {
            logger.info("Redis connection established");

            String result = null;
            if (jedis.exists(cacheKey)) {
                result = jedis.get(cacheKey);
            }
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }

    @PutMapping("/{cacheKey}/{cacheValue}")
    public void storeInCache(@PathVariable String cacheKey, @PathVariable String cacheValue) {
        logger.info(String.format("Storing %s in cache with key %s", cacheValue, cacheKey));
        try (JedisPool pool = new JedisPool(environment.getRedisHost(), environment.getRedisPort()); Jedis jedis = pool.getResource()) {
            jedis.set(cacheKey, cacheValue);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }
}
