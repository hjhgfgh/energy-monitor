package com.energy.process.dto;

/**
 * 推送给前端的实时消息。
 *
 * @param type     消息类型：{@code data} 新数据 / {@code alarm} 告警
 * @param data     载荷
 * @param timestamp 服务端时间戳
 */
public record RealtimeMessage(String type, Object data, long timestamp) {

    public static final String TYPE_DATA = "data";
    public static final String TYPE_ALARM = "alarm";

    public static RealtimeMessage data(Object payload) {
        return new RealtimeMessage(TYPE_DATA, payload, System.currentTimeMillis());
    }

    public static RealtimeMessage alarm(Object payload) {
        return new RealtimeMessage(TYPE_ALARM, payload, System.currentTimeMillis());
    }
}
