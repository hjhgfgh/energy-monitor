package com.energy.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.energy.server.entity.DeviceData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备数据 Mapper。
 */
public interface DeviceDataMapper extends BaseMapper<DeviceData> {

    /**
     * 批量插入，命中唯一键时更新。
     *
     * <p>两个作用合一：
     * <ul>
     *   <li><b>性能</b>：一条 SQL 插入整批（默认每批 500 条），相比逐条 insert，
     *       网络往返与事务开销从 N 次降为 1 次。</li>
     *   <li><b>幂等</b>：Kafka 至少一次语义下必然出现重复消费。
     *       依赖 {@code uk_device_time} 唯一索引，重复消息不会产生重复行。</li>
     * </ul>
     */
    @Insert("""
            <script>
            INSERT INTO device_data (device_id, voltage, electric_current, power, collect_time)
            VALUES
            <foreach collection="list" item="item" separator=",">
                (#{item.deviceId}, #{item.voltage}, #{item.electricCurrent}, #{item.power}, #{item.collectTime})
            </foreach>
            ON DUPLICATE KEY UPDATE
                voltage          = VALUES(voltage),
                electric_current = VALUES(electric_current),
                power            = VALUES(power)
            </script>
            """)
    int insertBatchIgnoreDuplicate(@Param("list") List<DeviceData> list);

    /**
     * 查询指定设备最近 N 条数据（大屏实时曲线用）。
     *
     * <p>刻意写成显式 SQL 而不是分页查询：这条查询走 {@code idx_device_time} 索引，
     * 是索引优化演示的对照对象。
     */
    @Select("""
            SELECT id, device_id, voltage, electric_current, power, collect_time, created_at
            FROM device_data
            WHERE device_id = #{deviceId}
              AND collect_time >= #{from}
            ORDER BY collect_time DESC
            LIMIT #{limit}
            """)
    List<DeviceData> selectRecentByDevice(@Param("deviceId") Long deviceId,
                                          @Param("from") LocalDateTime from,
                                          @Param("limit") int limit);

    /**
     * 查询每个设备的最新一条数据。
     *
     * <p>用子查询取 max(collect_time) 再回表，而不是对全表做分组——
     * 后者在千万级数据下会触发临时表与 filesort。
     */
    @Select("""
            SELECT d.*
            FROM device_data d
            INNER JOIN (
                SELECT device_id, MAX(collect_time) AS mt
                FROM device_data
                GROUP BY device_id
            ) latest ON d.device_id = latest.device_id AND d.collect_time = latest.mt
            """)
    List<DeviceData> selectLatestPerDevice();
}
