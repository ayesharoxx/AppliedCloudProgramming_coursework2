//package uk.ac.ed.acp.cw2.controller;
//
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import com.rabbitmq.client.Channel;
//import com.rabbitmq.client.Connection;
//import com.rabbitmq.client.ConnectionFactory;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.apache.kafka.clients.consumer.ConsumerRecords;
//import org.apache.kafka.clients.consumer.KafkaConsumer;
//import org.apache.kafka.clients.producer.KafkaProducer;
//import org.apache.kafka.clients.producer.ProducerRecord;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import uk.ac.ed.acp.cw2.data.RuntimeEnvironment;
//
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.time.Duration;
//import java.util.*;
//
//@RestController
//public class ProcessMessagesController {
//
//    private final RuntimeEnvironment environment;
//    private static final Logger logger = LoggerFactory.getLogger(ProcessMessagesController.class);
//
//    public ProcessMessagesController(RuntimeEnvironment environment) {
//        this.environment = environment;
//    }
//
//    @PostMapping("/processMessages")
//    public ResponseEntity<String> processMessages(@RequestBody Map<String, Object> request) {
//        String readTopic = (String) request.get("readTopic");
//        String writeQueueGood = (String) request.get("writeQueueGood");
//        String writeQueueBad = (String) request.get("writeQueueBad");
//        int messageCount = ((Number) request.get("messageCount")).intValue();
//
//        Gson gson = new Gson();
//        Properties kafkaProps = new KafkaController(environment).getKafkaProperties(environment);
//        kafkaProps.put("group.id", UUID.randomUUID().toString());
//        kafkaProps.setProperty("auto.offset.reset", "earliest");
//
//        double runningTotal = 0.0;
//
//        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
//            consumer.subscribe(Collections.singletonList(readTopic));
//            List<JsonObject> goodMessages = new ArrayList<>();
//            List<JsonObject> badMessages = new ArrayList<>();
//            int read = 0;
//
//            while (read < messageCount) {
//                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
//                for (ConsumerRecord<String, String> record : records) {
//                    JsonObject obj = JsonParser.parseString(record.value()).getAsJsonObject();
//                    String key = obj.get("key").getAsString();
//                    if (key.length() == 3 || key.length() == 4) {
//                        double value = obj.get("value").getAsDouble();
//                        runningTotal += value;
//                        obj.addProperty("runningTotalValue", runningTotal);
//
//                        // Store to ACP
//                        String uuid = postToACPStorage(obj);
//                        if (uuid != null) obj.addProperty("uuid", uuid);
//
//                        goodMessages.add(obj);
//                    } else if (key.length() == 5) {
//                        badMessages.add(obj);
//                    }
//                    read++;
//                    if (read >= messageCount) break;
//                }
//            }
//
//            JsonObject summary = new JsonObject();
//            summary.addProperty("uid", "s1234567");
//            summary.addProperty("key", "TOTAL");
//            summary.addProperty("comment", "");
//            summary.addProperty("value", runningTotal);
//
//            goodMessages.add(summary);
//            badMessages.add(summary);
//
//            sendToRabbitMQ(writeQueueGood, goodMessages);
//            sendToRabbitMQ(writeQueueBad, badMessages);
//
//            return ResponseEntity.ok("Processed " + messageCount + " messages");
//
//        } catch (Exception e) {
//            logger.error("Error in processing", e);
//            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
//        }
//    }
//
//    private String postToACPStorage(JsonObject json) {
//        try {
//            String endpoint = environment.getAcpStorageService() + "/api/v1/BLOB";
//            HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
//            con.setRequestMethod("POST");
//            con.setRequestProperty("Content-Type", "application/json");
//            con.setDoOutput(true);
//
//            try (OutputStream os = con.getOutputStream()) {
//                os.write(json.toString().getBytes());
//            }
//
//            Scanner s = new Scanner(con.getInputStream()).useDelimiter("\\A");
//            String response = s.hasNext() ? s.next() : "";
//            return JsonParser.parseString(response).getAsJsonObject().get("uuid").getAsString();
//        } catch (Exception e) {
//            logger.warn("ACP Storage failed", e);
//            return null;
//        }
//    }
//
//    private void sendToRabbitMQ(String queue, List<JsonObject> messages) throws Exception {
//        ConnectionFactory factory = new ConnectionFactory();
//        factory.setHost(environment.getRabbitMqHost());
//        factory.setPort(environment.getRabbitMqPort());
//
//        try (Connection conn = factory.newConnection(); Channel channel = conn.createChannel()) {
//            channel.queueDeclare(queue, false, false, false, null);
//            for (JsonObject msg : messages) {
//                channel.basicPublish("", queue, null, msg.toString().getBytes());
//            }
//        }
//    }
//}
