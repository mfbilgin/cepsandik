package com.cepsandik.electionservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ bildirim kuyruğu yapılandırması.
 *
 * Yapı:
 * - notification.exchange (Topic Exchange)
 *   ├── notification.election.queue (routing key: notification.election.*)
 *   └── notification.election.dlq (Dead Letter Queue)
 */
@Configuration
public class NotificationRabbitConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String ELECTION_NOTIFICATION_QUEUE = "notification.election.queue";
    public static final String ELECTION_NOTIFICATION_ROUTING_KEY = "notification.election";

    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String NOTIFICATION_DLQ = "notification.election.dlq";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.election.dead";

    // Dead Letter Exchange
    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(NOTIFICATION_DLX);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder
                .bind(notificationDeadLetterQueue())
                .to(notificationDeadLetterExchange())
                .with(NOTIFICATION_DLQ_ROUTING_KEY);
    }

    // Main Exchange (Topic for flexibility)
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    // Main Queue
    @Bean
    public Queue electionNotificationQueue() {
        return QueueBuilder
                .durable(ELECTION_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding electionNotificationBinding() {
        return BindingBuilder
                .bind(electionNotificationQueue())
                .to(notificationExchange())
                .with(ELECTION_NOTIFICATION_ROUTING_KEY + ".#");
    }

    // JSON Message Converter
    @Bean
    public MessageConverter notificationJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(notificationJsonMessageConverter());
        return rabbitTemplate;
    }
}
