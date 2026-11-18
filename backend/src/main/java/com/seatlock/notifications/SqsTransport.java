package com.seatlock.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${seatlock.notifications}' == 'sqs' and '${seatlock.worker}' == 'true'")
public class SqsTransport implements NotificationPublisher {
    private final String queueUrl;
    private final ObjectMapper json;

    public SqsTransport(@Value("${SQS_QUEUE_URL:}") String queueUrl, ObjectMapper json) {
        URI url = URI.create(queueUrl);
        if (!"https".equals(url.getScheme()) || url.getHost() == null
                || !url.getHost().matches("sqs\\.[a-z0-9-]+\\.amazonaws\\.com")) {
            throw new IllegalArgumentException("SQS_QUEUE_URL must be an AWS SQS HTTPS URL.");
        }
        this.queueUrl = queueUrl;
        this.json = json;
    }

    @Override
    public void publish(Delivery.Message message) {
        Process process = null;
        try {
            byte[] input = json.writeValueAsBytes(Map.of("QueueUrl",queueUrl,"MessageBody",json.writeValueAsString(message)));
            process = new ProcessBuilder("aws","sqs","send-message","--cli-input-json","file:///dev/stdin",
                "--cli-connect-timeout","4","--cli-read-timeout","5","--no-cli-pager")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var out = process.getOutputStream()) { out.write(input); }
            if (!process.waitFor(12,TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("SQS did not acknowledge the notification.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SQS publication was interrupted.",error);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("SQS publication failed.",error);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
