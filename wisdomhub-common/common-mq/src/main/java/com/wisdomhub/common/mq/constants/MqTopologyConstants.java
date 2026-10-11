package com.wisdomhub.common.mq.constants;

/**
 * MQ 拓扑命名：全项目只有一个 topic 交换机。
 * 队列与死信队列由各消费者服务自己声明，不往 common 层堆。
 */
public final class MqTopologyConstants {

    /** 领域事件交换机，durable、非 auto-delete；路由键见 {@link MqRoutingKeyConstants} */
    public static final String EVENT_EXCHANGE = "wisdomhub.event";

    /** 死信交换机（direct）。业务队列靠 x-dead-letter-exchange 指过来，路由键用队列自己的名字 */
    public static final String DLX_EXCHANGE = "wisdomhub.dlx";

    /** 死信队列后缀，业务队列 {@code xxx.q} 对应 {@code xxx.q.dlq} */
    public static final String DLQ_SUFFIX = ".dlq";

    private MqTopologyConstants() {
    }
}
