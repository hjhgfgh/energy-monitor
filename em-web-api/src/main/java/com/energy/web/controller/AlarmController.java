package com.energy.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.dto.Result;
import com.energy.common.entity.Alarm;
import com.energy.common.mapper.AlarmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警查询接口。
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmMapper alarmMapper;

    /** 告警记录分页，默认按时间倒序 */
    @GetMapping
    public Result<IPage<Alarm>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(alarmMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Alarm>().orderByDesc(Alarm::getCreatedAt)));
    }

    /** 未处理告警数，大屏角标用 */
    @GetMapping("/unhandled-count")
    public Result<Long> unhandledCount() {
        return Result.ok(alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>().eq(Alarm::getStatus, 0)));
    }
}
