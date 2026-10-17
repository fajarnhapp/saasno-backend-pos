package com.saasno.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    public static final String EXCHANGE_NAME = "notification-exchange";
    public static final String QUEUE_NAME = "notification-queue";
    public static final String ROUTING_KEY = "notification.key";

    // Queue terpisah untuk job download — concurrency di consumer-nya (lihat DownloadConsumer)
    // sengaja dikunci ke 1, jadi RabbitMQ otomatis menahan job lain di antrian sampai job yang
    // sedang berjalan selesai, satu per satu.
    public static final String DOWNLOAD_EXCHANGE_NAME = "download-exchange";
    public static final String DOWNLOAD_QUEUE_NAME = "download-queue";
    public static final String DOWNLOAD_ROUTING_KEY = "download.key";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public TopicExchange downloadExchange() {
        return new TopicExchange(DOWNLOAD_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue downloadQueue() {
        return new Queue(DOWNLOAD_QUEUE_NAME, true);
    }

    @Bean
    public Binding downloadBinding() {
        // Panggil langsung method @Bean lain di kelas @Configuration yang sama (bukan lewat
        // parameter injeksi) supaya tidak ambigu dengan queue()/exchange() milik notifikasi —
        // Spring tetap mengembalikan instance singleton yang sama berkat proxy @Configuration.
        return BindingBuilder.bind(downloadQueue()).to(downloadExchange()).with(DOWNLOAD_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }
}
