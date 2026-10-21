package com.wisdomhub.common.mq.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.wisdomhub.common.mq.constants.MqTopologyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.support.AmqpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** 扫描 .dlq，换新 eventId 后按原队列名直接投回业务队列。 */
@Slf4j
@RequiredArgsConstructor
public class DeadLetterReplayService {

    private static final String REPLAY_COUNT_HEADER = "x-wisdomhub-replay-count";
    private static final String DEATH_HEADER = "x-death";
    private static final String FIRST_DEATH_PREFIX = "x-first-death-";
    private static final String LAST_DEATH_PREFIX = "x-last-death-";

    private final ConnectionFactory connectionFactory;
    private final AmqpAdmin amqpAdmin;
    private final ObjectMapper objectMapper;
    private final DlqReplayProperties properties;

    private final DefaultMessagePropertiesConverter propertiesConverter = new DefaultMessagePropertiesConverter();

    public DlqReplayResult replayAll() {
        DlqReplayResult result = new DlqReplayResult();
        if (properties.getQueues() == null) {
            return result;
        }
        for (String queue : properties.getQueues()) {
            replayQueue(queue, result);
        }
        return result;
    }

    private void replayQueue(String queue, DlqReplayResult result) {
        String dlq = queue + MqTopologyConstants.DLQ_SUFFIX;
        if (amqpAdmin.getQueueProperties(dlq) == null) {
            log.debug("死信队列不存在, 跳过: {}", dlq);
            return;
        }
        if (amqpAdmin.getQueueProperties(queue) == null) {
            log.error("业务队列不存在, 无法重投: {}", queue);
            result.failed(queue);
            return;
        }

        try (Connection connection = connectionFactory.createConnection(); Channel channel = connection.createChannel(false)) {
            // 开启confirm确认
            channel.confirmSelect();
            int processed = 0;
            while (processed < properties.getBatchSize()) {
                GetResponse response = channel.basicGet(dlq, false);
                if (response == null) {
                    break;
                }
                processed++;
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                switch (replayOne(channel, queue, response)) {
                    case REPLAYED -> {
                        channel.basicAck(deliveryTag, false);
                        result.replayed(queue);
                    }
                    case EXHAUSTED -> {
                        nackQuietly(channel, deliveryTag, queue);
                        result.exhausted(queue);
                        return;
                    }
                    case INVALID -> {
                        nackQuietly(channel, deliveryTag, queue);
                        result.invalid(queue);
                        return;
                    }
                    case FAILED -> {
                        nackQuietly(channel, deliveryTag, queue);
                        result.failed(queue);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("死信重投失败, queue={}", queue, e);
            result.failed(queue);
        }
    }

    private Outcome replayOne(Channel channel, String queue, GetResponse response) throws IOException, TimeoutException {
        MessageProperties messageProperties = propertiesConverter.toMessageProperties(
                response.getProps(), response.getEnvelope(), StandardCharsets.UTF_8.name());
        Map<String, Object> headers = messageProperties.getHeaders() == null
                ? new HashMap<>()
                : new HashMap<>(messageProperties.getHeaders());
        int replayCount = replayCount(headers.get(REPLAY_COUNT_HEADER));
        if (replayCount >= properties.getMaxReplays()) {
            log.error("死信重投已达上限, queue={}, eventId={}, replays={}", queue, messageProperties.getMessageId(), replayCount);
            return Outcome.EXHAUSTED;
        }

        ReplayBody replayBody;
        try {
            replayBody = rewriteEventId(response.getBody());
        } catch (IOException e) {
            log.error("死信消息体不是合法 JSON, queue={}, eventId={}", queue, messageProperties.getMessageId(), e);
            return Outcome.INVALID;
        }
        if (replayBody == null) {
            log.error("死信消息体缺少 eventId, queue={}, eventId={}", queue, messageProperties.getMessageId());
            return Outcome.INVALID;
        }

        headers.remove(DEATH_HEADER);
        headers.remove(AmqpHeaders.DELIVERY_TAG);
        headers.remove(MessageProperties.RETRY_COUNT);
        headers.keySet().removeIf(key -> key.startsWith(FIRST_DEATH_PREFIX) || key.startsWith(LAST_DEATH_PREFIX));
        headers.put(REPLAY_COUNT_HEADER, replayCount + 1);
        messageProperties.setHeaders(headers);
        messageProperties.setRetryCount(0);
        messageProperties.setMessageId(replayBody.newEventId());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (messageProperties.getContentType() == null) {
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        }
        AMQP.BasicProperties basicProperties = propertiesConverter.fromMessageProperties(messageProperties, StandardCharsets.UTF_8.name());

        channel.basicPublish("", queue, basicProperties, replayBody.body());
        try {
            channel.waitForConfirmsOrDie(properties.getConfirmTimeoutMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("死信重投等待确认被中断, queue={}, oldEventId={}, newEventId={}",
                    queue, replayBody.oldEventId(), replayBody.newEventId(), e);
            return Outcome.FAILED;
        } catch (IOException | TimeoutException e) {
            log.error("死信重投未被 broker 确认, queue={}, oldEventId={}, newEventId={}",
                    queue, replayBody.oldEventId(), replayBody.newEventId(), e);
            return Outcome.FAILED;
        }
        log.info("死信重投成功, queue={}, oldEventId={}, newEventId={}, replays={}",
                queue, replayBody.oldEventId(), replayBody.newEventId(), replayCount + 1);
        return Outcome.REPLAYED;
    }

    private ReplayBody rewriteEventId(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode idNode = root.get("eventId");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            return null;
        }
        String oldEventId = idNode.asText();
        String newEventId = UUID.randomUUID().toString();
        ((ObjectNode) root).put("eventId", newEventId);
        return new ReplayBody(objectMapper.writeValueAsBytes(root), oldEventId, newEventId);
    }

    private static int replayCount(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static void nackQuietly(Channel channel, long deliveryTag, String queue) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (Exception e) {
            log.warn("死信消息 nack 失败, 依赖连接关闭后重投, queue={}", queue, e);
        }
    }

    private enum Outcome {
        REPLAYED, EXHAUSTED, INVALID, FAILED
    }

    private record ReplayBody(byte[] body, String oldEventId, String newEventId) {
    }
}
