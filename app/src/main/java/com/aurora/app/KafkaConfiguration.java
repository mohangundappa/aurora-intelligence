package com.aurora.app;

import com.aurora.common.EventEnvelope;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
@Profile("!test")
public class KafkaConfiguration {
  @Bean
  public ProducerFactory<String, EventEnvelope> producerFactory() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        System.getenv().getOrDefault("REDPANDA_BROKERS", "redpanda:9092"));
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  public KafkaTemplate<String, EventEnvelope> kafkaTemplate(
      ProducerFactory<String, EventEnvelope> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public ConsumerFactory<String, EventEnvelope> consumerFactory() {
    JsonDeserializer<EventEnvelope> deserializer = new JsonDeserializer<>(EventEnvelope.class);
    deserializer.addTrustedPackages("com.aurora.common");
    Map<String, Object> properties = new HashMap<>();
    properties.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        System.getenv().getOrDefault("REDPANDA_BROKERS", "redpanda:9092"));
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "aurora-signal-consumer");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), deserializer);
  }

  @Bean
  public NewTopic rawEventsTopic() {
    return TopicBuilder.name("aurora.events.raw.v1").partitions(1).replicas(1).build();
  }
}
