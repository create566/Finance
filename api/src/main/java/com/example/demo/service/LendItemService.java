package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface LendItemService extends IService<LendItem> {

    String buildInvestForm(Long lendId, Long userId, BigDecimal amount);

    boolean cancelInvest(Long lendItemId, Long userId);

    boolean handleInvestNotify(Map<String, Object> params);

    List<LendItem> listByLendId(Long lendId);

    Page<LendItem> pageList(Integer pageNum, Integer pageSize, Long lendId, Long investUserId);

    User getUserForInvest(Long userId);

    UserBind getBindForInvest(Long userId);

    BigDecimal getBalanceForInvest(Long userId);
}