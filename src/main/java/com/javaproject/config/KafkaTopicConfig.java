package com.javaproject.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic liveStockPricesTopic() {
        return TopicBuilder.name("live-stock-prices")
                .partitions(1)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic stockTopic() {
        // Topic Name, Partitions, Replication Factor
        return TopicBuilder.name("stock-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }


    @Bean
    public NewTopic stockCommand() {
        // Topic Name, Partitions, Replication Factor
        return TopicBuilder.name("stock-command")
                .partitions(1)
                .replicas(1)
                .build();
    }
}