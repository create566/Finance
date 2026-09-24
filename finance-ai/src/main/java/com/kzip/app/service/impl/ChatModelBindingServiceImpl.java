package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.mapper.ChatModelBindingMapper;
import com.kzip.app.service.ChatModelBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ChatModelBindingServiceImpl extends ServiceImpl<ChatModelBindingMapper, ChatModelBinding> implements ChatModelBindingService {

    @Override
    public List<ChatModelBinding> listAll() {
        return this.baseMapper.selectAllEnabled();
    }

    @Override
    @Transactional
    public boolean updateBinding(ChatModelBinding binding) {
        if (binding.getId() == null) {
            throw new RuntimeException("ID不能为空");
        }
        ChatModelBinding exist = this.getById(binding.getId());
        if (exist == null) {
            throw new RuntimeException("绑定配置不存在");
        }
        if (binding.getKbId() != null && binding.getKbId() == 0) {
            binding.setKbId(null);
            binding.setKbName("—");
        }
        return this.updateById(binding);
    }

    @Override
    public ChatModelBinding getByScene(String scene) {
        return this.baseMapper.selectByScene(scene);
    }
}