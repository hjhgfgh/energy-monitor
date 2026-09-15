package com.energy.simulator;

import lombok.Data;
import lombok.ToString;

/**
 * 模拟器启动参数，支持命令行覆盖：
 * {@code java -jar em-simulator.jar [host] [port] [deviceCount] [intervalMs] [startDeviceId]}
 */
@Data
@ToString
public class SimulatorOptions {

    private String host = "127.0.0.1";
    private int port = 9000;
    private int deviceCount = 5;
    private long intervalMs = 1000L;
    private long startDeviceId = 1001L;

    public static SimulatorOptions fromArgs(String[] args) {
        SimulatorOptions options = new SimulatorOptions();
        if (args.length > 0 && !args[0].isBlank()) {
            options.setHost(args[0]);
        }
        if (args.length > 1 && !args[1].isBlank()) {
            options.setPort(Integer.parseInt(args[1]));
        }
        if (args.length > 2 && !args[2].isBlank()) {
            options.setDeviceCount(Integer.parseInt(args[2]));
        }
        if (args.length > 3 && !args[3].isBlank()) {
            options.setIntervalMs(Long.parseLong(args[3]));
        }
        if (args.length > 4 && !args[4].isBlank()) {
            options.setStartDeviceId(Long.parseLong(args[4]));
        }
        return options;
    }
}
