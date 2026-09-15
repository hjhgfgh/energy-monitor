package com.energy.server.controller;

import com.energy.server.dto.Result;
import com.energy.server.websocket.DeviceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时推送状态接口。
 *
 * <p>暴露在线连接数，用于验证推送链路是否真的建立了连接——
 * 大屏「没数据」时，第一件事是确认连接在不在。
 */
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final DeviceWebSocketHandler webSocketHandler;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("endpoint", "/ws/realtime");
        status.put("onlineClients", webSocketHandler.onlineCount());
        return Result.ok(status);
    }
}
