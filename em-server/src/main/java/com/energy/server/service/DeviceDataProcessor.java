package com.energy.server.service;

import com.energy.common.protocol.DeviceFrame;

/**
 * 设备数据处理器：接收已解析成功的设备帧并做业务处理。
 *
 * <p>抽出接口是为了让 P1（日志处理）能平滑演进到 P2/P3（Kafka 投递、落库、告警判定），
 * 而不需要改动 Netty 流水线。
 */
public interface DeviceDataProcessor {

    /**
     * 处理一个设备帧。
     *
     * <p>实现必须自行保证不抛异常——该方法运行在 Netty IO 线程上，
     * 抛异常会触发连接关闭，可能影响同一条 EventLoop 上的其他连接。
     */
    void process(DeviceFrame frame);
}
