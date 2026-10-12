package com.example.demo.service;

import com.example.demo.entity.vo.PlatformStatisticsVO;

/**
 * 平台运营统计。
 *
 * <p>登录 / 注册页是<b>未登录</b>状态，拿不到任何业务数据，因此这里提供一个
 * 只读、无鉴权的聚合口径接口（在白名单中显式放行），供登录页展示真实运营数据。</p>
 */
public interface IndexStatisticsService {

    /**
     * 实时聚合平台运营指标（口径见实现类注释）。
     */
    PlatformStatisticsVO platformStatistics();
}
