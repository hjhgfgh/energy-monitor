package com.energy.server.controller;

import com.energy.server.dto.Result;
import com.energy.server.entity.Device;
import com.energy.server.entity.DeviceData;
import com.energy.server.exception.BusinessException;
import com.energy.server.exception.GlobalExceptionHandler;
import com.energy.server.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备查询接口测试。
 *
 * <p>刻意用 {@code standaloneSetup} 而非 {@code @WebMvcTest}：
 * 后者会拉起 Spring 上下文并尝试装配 MyBatis-Plus 自动配置（需要真实 DataSource），
 * 对一个纯 Web 层契约测试来说过重。standalone 只装配 Controller + 异常处理器，
 * 毫秒级完成，且同样能验证「路径匹配 + 响应结构 + 异常兜底」这三件真正要测的事。
 */
@DisplayName("设备查询接口")
class DeviceControllerTest {

    private DeviceService deviceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceService = mock(DeviceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DeviceController(deviceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Device aDevice(long id, String code, String name) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceCode(code);
        device.setName(name);
        device.setType("METER");
        return device;
    }

    private static DeviceData aData(long deviceId) {
        DeviceData data = new DeviceData();
        data.setDeviceId(deviceId);
        data.setVoltage(new BigDecimal("219.38"));
        data.setElectricCurrent(new BigDecimal("5.17"));
        data.setPower(new BigDecimal("1134.95"));
        data.setCollectTime(LocalDateTime.now());
        return data;
    }

    @Test
    @DisplayName("设备列表返回统一响应体，code=0")
    void listDevices_returnsOk() throws Exception {
        given(deviceService.listDevices()).willReturn(List.of(
                aDevice(1001L, "METER-A-001", "1号车间总表"),
                aDevice(1002L, "METER-A-002", "2号车间总表")));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.CODE_SUCCESS))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].name").value("1号车间总表"));
    }

    @Test
    @DisplayName("设备详情按主键查询")
    void getDevice_returnsDetail() throws Exception {
        given(deviceService.getDevice(1001L))
                .willReturn(aDevice(1001L, "METER-A-001", "1号车间总表"));

        mockMvc.perform(get("/api/devices/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.CODE_SUCCESS))
                .andExpect(jsonPath("$.data.deviceCode").value("METER-A-001"));
    }

    @Test
    @DisplayName("设备不存在时由全局异常处理转成业务错误码，而不是 500")
    void getDevice_notFound_returnsBusinessError() throws Exception {
        given(deviceService.getDevice(9999L))
                .willThrow(new BusinessException("设备不存在: id=9999"));

        mockMvc.perform(get("/api/devices/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.CODE_BUSINESS_ERROR))
                .andExpect(jsonPath("$.message").value("设备不存在: id=9999"));
    }

    @Test
    @DisplayName("latest-data 不被 /{id} 抢占，证明路径正则约束生效")
    void latestData_notShadowedByIdPath() throws Exception {
        given(deviceService.latestPerDevice()).willReturn(List.of(aData(1001L)));

        mockMvc.perform(get("/api/devices/latest-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.CODE_SUCCESS))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviceId").value(1001));
    }

    @Test
    @DisplayName("recent 接口带默认参数时按 24 小时 / 100 条查询")
    void recentData_usesDefaults() throws Exception {
        given(deviceService.recentData(1001L, 24, 100)).willReturn(List.of(aData(1001L)));

        mockMvc.perform(get("/api/devices/1001/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.CODE_SUCCESS))
                .andExpect(jsonPath("$.data[0].voltage").value(219.38));
    }

    @Test
    @DisplayName("recent 接口显式参数被正确透传")
    void recentData_passesExplicitParams() throws Exception {
        given(deviceService.recentData(1001L, 2, 5)).willReturn(List.of(aData(1001L)));

        mockMvc.perform(get("/api/devices/1001/recent?hours=2&limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("非数字路径变量不匹配 {id:\\d+}，返回 404 而非被兜底成 500")
    void nonNumericId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/devices/abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(Result.CODE_NOT_FOUND));
    }
}
