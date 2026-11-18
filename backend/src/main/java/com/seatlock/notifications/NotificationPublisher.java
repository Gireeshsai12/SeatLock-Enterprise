package com.seatlock.notifications;

public interface NotificationPublisher {
    void publish(Delivery.Message message);
}
