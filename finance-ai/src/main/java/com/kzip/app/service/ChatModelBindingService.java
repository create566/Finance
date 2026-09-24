package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ChatModelBinding;

import java.util.List;

public interface ChatModelBindingService extends IService<ChatModelBinding> {

    List<ChatModelBinding> listAll();

    boolean updateBinding(ChatModelBinding binding);

    ChatModelBinding getByScene(String scene);
}