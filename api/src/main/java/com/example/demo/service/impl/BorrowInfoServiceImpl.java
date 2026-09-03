package com.example.demo.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.mapper.BorrowInfoMapper;
import com.example.demo.service.BorrowInfoService;
import org.springframework.stereotype.Service;
@Service
public class BorrowInfoServiceImpl extends ServiceImpl<BorrowInfoMapper, BorrowInfo> implements BorrowInfoService {
}