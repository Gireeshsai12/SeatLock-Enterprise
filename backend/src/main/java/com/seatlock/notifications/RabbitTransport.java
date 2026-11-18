package com.seatlock.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${seatlock.notifications}' == 'rabbit' and '${seatlock.worker}' == 'true'")
public class RabbitTransport implements NotificationPublisher {
    static final String QUEUE = "seatlock.notifications";
    static final String DEAD = "seatlock.notifications.dead";
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final Delivery delivery;

    public RabbitTransport(RabbitTemplate rabbit, ObjectMapper json, Delivery delivery) {
        this.rabbit = rabbit;
        this.json = json;
        this.delivery = delivery;
        rabbit.setMandatory(true);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DEAD).build();
    }

    @Bean
    Queue deadQueue() { return QueueBuilder.durable(DEAD).build(); }

    @Override
    public void publish(Delivery.Message message) {
        var correlation = new CorrelationData(message.id().toString());
        try {
            rabbit.convertAndSend("",QUEUE,json.writeValueAsString(message),correlation);
            var confirm = correlation.getFuture().get(Duration.ofSeconds(5).toMillis(),TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Broker did not route the notification.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publishing was interrupted.",error);
        } catch (Exception error) {
            throw new IllegalStateException("Notification was not acknowledged.",error);
        }
    }

    @RabbitListener(queues=QUEUE)
    public void consume(String body) throws com.fasterxml.jackson.core.JsonProcessingException {
        delivery.deliver(json.readValue(body,Delivery.Message.class));
    }
}
