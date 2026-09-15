package com.energy.server.config;

/**
 * Kafka topic 与消费者组常量，集中一处避免字符串散落。
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /**
     * 设备上报数据。
     *
     * <p>分区数决定消费并行度上限，建 topic 时设为 3（与本机资源匹配）。
     * 消息 key 用 deviceId，保证同一设备的数据进同一分区、分区内有序——
     * 这是「乱序导致曲线跳变」这类问题的根治手段。
     */
    public static final String DEVICE_DATA = "device-data";

    /** 数据落库消费组 */
    public static final String GROUP_DATA_PERSIST = "em-data-persist";

    /** 告警计算消费组（与落库组独立，各自消费全量数据） */
    public static final String GROUP_ALARM = "em-alarm";
}
