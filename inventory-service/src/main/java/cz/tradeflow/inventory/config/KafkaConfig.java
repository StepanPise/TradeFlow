package cz.tradeflow.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic reservationResultsTopic() {
        return TopicBuilder.name("inventory.reservation-results")
                .partitions(1)
                .replicas(1)
                .build();
    }
}