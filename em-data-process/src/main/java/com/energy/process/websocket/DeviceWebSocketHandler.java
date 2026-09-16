package com.energy.process.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.energy.process.dto.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时数据推送端点。
 *
 * <p><b>为什么用 WebSocket 而不是轮询：</b>大屏需要秒级刷新。轮询意味着
 * 绝大多数请求都是空结果，白白消耗连接与数据库资源，且延迟受轮询间隔限制。
 * WebSocket 是全双工长连接，服务端有数据才推，延迟即网络往返。
 *
 * <p><b>两个关键实现点：</b>
 * <ol>
 *   <li>用 {@link ConcurrentWebSocketSessionDecorator} 包装原始 session。
 *       原始 {@code WebSocketSession} 的 {@code sendMessage} 不是线程安全的，
 *       而推送来自 Kafka 消费线程，并发写同一连接会直接抛
 *       {@code IllegalStateException: TEXT_PARTIAL_WRITING} 并断开连接。</li>
 *   <li>推送是「尽力而为」：单个连接发送失败只移除该连接，不影响其他订阅者。
 *       大屏推送丢一帧无所谓，下一帧就补上了；但不能因为一个坏连接拖垮整个推送。</li>
 * </ol>
 *
 * <p><b>多实例部署时的局限：</b>当前 session 存在本进程内存里，
 * 多实例时只能推给「连到自己这个实例」的客户端。要跨实例推送，
 * 需要把消息投到 Redis Pub/Sub 或 Kafka，各实例订阅后推给自己持有的连接。
 * 本项目按单实例部署，此处如实说明边界。
 */
@Slf4j
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    /** 单连接发送缓冲上限 512KB、超时 5 秒，防止慢客户端拖垮内存 */
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public DeviceWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        sessions.put(session.getId(), safeSession);
        log.info("WebSocket 连接建立: {}，当前在线 {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket 连接断开: {}（{}），当前在线 {}",
                session.getId(), status, sessions.size());
    }

    /** 广播给所有在线客户端 */
    public void broadcast(RealtimeMessage message) {
        if (sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("实时消息序列化失败", e);
            return;
        }

        TextMessage text = new TextMessage(json);
        sessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(text);
                } else {
                    sessions.remove(id);
                }
            } catch (Exception e) {
                log.warn("推送失败，移除连接 {}: {}", id, e.getMessage());
                sessions.remove(id);
            }
        });
    }

    public int onlineCount() {
        return sessions.size();
    }
}
