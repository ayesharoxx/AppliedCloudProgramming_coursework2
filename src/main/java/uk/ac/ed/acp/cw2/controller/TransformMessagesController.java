package uk.ac.ed.acp.cw2.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import uk.ac.ed.acp.cw2.data.RuntimeEnvironment;

import java.io.IOException;
import java.util.*;

@RestController
public class TransformMessagesController {

    private static final Logger logger = LoggerFactory.getLogger(TransformMessagesController.class);
    private final RuntimeEnvironment environment;

    public TransformMessagesController(RuntimeEnvironment environment) {
        this.environment = environment;
    }

    @PostMapping("/transformMessages")
    public ResponseEntity<String> transformMessages(@RequestBody Map<String, Object> request) {
        String readQueue = (String) request.get("readQueue");
        String writeQueue = (String) request.get("writeQueue");
        int messageCount = ((Number) request.get("messageCount")).intValue();

        int processed = 0, written = 0, updated = 0;
        double totalValue = 0.0, totalAdded = 0.0;

        try (
                JedisPool pool = new JedisPool(environment.getRedisHost(), environment.getRedisPort());
                Jedis jedis = pool.getResource()
        ) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(environment.getRabbitMqHost());
            factory.setPort(environment.getRabbitMqPort());

            try (Connection conn = factory.newConnection(); Channel channel = conn.createChannel()) {

                channel.queueDeclare(readQueue, false, false, false, null);
                channel.queueDeclare(writeQueue, false, false, false, null);

                for (int i = 0; i < messageCount; i++) {
                    GetResponse response = channel.basicGet(readQueue, true);
                    if (response == null) {
                        Thread.sleep(100); // slight delay if queue is briefly empty
                        i--;
                        continue;
                    }

                    String body = new String(response.getBody());
                    JsonObject msg = JsonParser.parseString(body).getAsJsonObject();
                    processed++;

                    if (!msg.has("version")) {
                        // Tombstone
                        jedis.del(msg.get("key").getAsString());
                        JsonObject summary = new JsonObject();
                        summary.addProperty("totalMessagesWritten", written);
                        summary.addProperty("totalMessagesProcessed", processed);
                        summary.addProperty("totalRedisUpdates", updated);
                        summary.addProperty("totalValueWritten", totalValue);
                        summary.addProperty("totalAdded", totalAdded);

                        channel.basicPublish("", writeQueue, null, summary.toString().getBytes());

                        continue;
                    }

                    String key = msg.get("key").getAsString();
                    int version = msg.get("version").getAsInt();
                    double value = msg.get("value").getAsDouble();
                    boolean isUpdate = false;

                    String redisVersionStr = jedis.get(key);
                    if (redisVersionStr == null || Integer.parseInt(redisVersionStr) < version) {
                        jedis.set(key, String.valueOf(version));
                        msg.addProperty("value", value + 10.5);
                        totalAdded += 10.5;
                        totalValue += value + 10.5;
                        updated++;
                        isUpdate = true;
                    } else {
                        totalValue += value;
                    }

                    channel.basicPublish("", writeQueue, null, msg.toString().getBytes());
                    written++;
                }

                return ResponseEntity.ok("Processed " + processed + " messages. Written: " + written);
            }

        } catch (Exception e) {
            logger.error("Transform failed", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
