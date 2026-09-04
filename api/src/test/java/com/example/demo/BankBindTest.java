package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.example.demo.utils.TokenUtil;
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 银行绑定全流程测试
 * 模拟：注册用户 → 登录 → 发起绑卡 → 银行异步回调
 */
@SpringBootTest
class BankBindTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    private static final Random RANDOM = new Random();

    private Long userId;
    private String token;
    private String mobile;
    private String idCard;
    private String bankName;
    private String bankCard;

    private String randomChineseName() {
        String[] firstNames = {"张", "李", "王", "赵", "陈", "刘", "杨", "黄", "周", "吴"};
        String[] lastNames = {"伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "洋"};
        return firstNames[RANDOM.nextInt(firstNames.length)] + lastNames[RANDOM.nextInt(lastNames.length)];
    }

    private String randomMobile() {
        return String.format("1%d%09d", 3 + RANDOM.nextInt(7), RANDOM.nextInt(1000000000));
    }

    private String randomIdCard() {
        String[] areas = {"110101", "310101", "440101", "440301", "320101", "330101"};
        String area = areas[RANDOM.nextInt(areas.length)];
        String birth = String.format("%04d%02d%02d",
                1970 + RANDOM.nextInt(50),
                1 + RANDOM.nextInt(12),
                1 + RANDOM.nextInt(28));
        String seq = String.format("%03d", RANDOM.nextInt(1000));
        String[] checks = "0123456789X".split("");
        String check = checks[RANDOM.nextInt(checks.length)];
        return area + birth + seq + check;
    }

    private String randomBankCard() {
        return String.valueOf(6200 + RANDOM.nextInt(10000)) + String.format("%012d", RANDOM.nextInt(1000000000));
    }

    private String randomBankName() {
        String[] banks = {"中国工商银行", "中国建设银行", "中国农业银行", "中国银行", "交通银行", "招商银行"};
        return banks[RANDOM.nextInt(banks.length)];
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mobile = randomMobile();
        idCard = randomIdCard();
        bankName = randomBankName();
        bankCard = randomBankCard();
        LoginUserContext.clear();
    }

    @Test
    void testBankBindFlow() throws Exception {
        // 1. 创建用户
        User user = new User();
        user.setMobile(mobile);
        user.setName(randomChineseName());
        user.setNickName(user.getName());
        user.setPassword("123456");
        user.setUserType(1);
        user.setStatus(1);
        user.setIdCard(idCard);
        user.setEmail("test@test.com");
        userService.save(user);
        userId = user.getId();
        assertNotNull(userId, "用户ID不应为空");

        // 2. 生成token
        token = TokenUtil.generateMerchantToken(userId.toString());
        assertTrue(token.startsWith("5grcs "), "token应以 '5grcs ' 开头");

        // 3. 发起绑卡请求
        System.out.println("=== 3. 发起绑卡 ===");
        MvcResult bindResult = mockMvc.perform(get("/api/user/bind")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        String bindHtml = bindResult.getResponse().getContentAsString();
        System.out.println("绑定表单HTML长度: " + bindHtml.length());
        assertTrue(bindHtml.contains("<form"), "绑卡响应应包含form表单");
        assertTrue(bindHtml.contains("action=\"http://localhost:9090/userBind/BindAgreeUserV2\""),
                "绑卡表单应提交到银行平台");
        assertTrue(bindHtml.contains(idCard), "绑卡表单应包含身份证号");

        // 4. 模拟银行异步回调
        System.out.println("=== 4. 模拟银行回调 ===");
        String bindCode = "BIND" + System.currentTimeMillis();
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", "999888");
        paramMap.put("agentUserId", mobile);
        paramMap.put("personalName", user.getName());
        paramMap.put("mobile", mobile);
        paramMap.put("idCard", idCard);
        paramMap.put("bindCode", bindCode);
        paramMap.put("status", "1");
        paramMap.put("bankNo", bankCard);

        String sign = BankSignUtil.getSign(paramMap);
        paramMap.put("sign", sign);

        MvcResult notifyResult = mockMvc.perform(post("/api/core/userBind/notify")
                        .param("agentId", "999888")
                        .param("agentUserId", mobile)
                        .param("personalName", user.getName())
                        .param("mobile", mobile)
                        .param("idCard", idCard)
                        .param("bindCode", bindCode)
                        .param("status", "1")
                        .param("bankNo", bankCard)
                        .param("sign", sign))
                .andExpect(status().isOk())
                .andReturn();
        String notifyResponse = notifyResult.getResponse().getContentAsString();
        System.out.println("回调响应: " + notifyResponse);
        assertEquals("success", notifyResponse, "银行回调应返回success");

        // 5. 验证绑定记录已保存
        System.out.println("=== 5. 验证绑定记录 ===");
        UserBind userBind = userBindService.getByUserId(userId);
        assertNotNull(userBind, "绑定记录不应为空");
        assertEquals(bindCode, userBind.getBindCode(), "绑定协议号应匹配");
        assertEquals(idCard, userBind.getIdCard(), "身份证号应匹配");
        assertEquals(1, userBind.getStatus().intValue(), "绑定状态应为1(成功)");

        // 6. 验证用户bindStatus和bindCode已更新
        User updatedUser = userService.getById(userId);
        assertNotNull(updatedUser, "用户不应为空");
        assertEquals(1, updatedUser.getBindStatus(), "用户绑定状态应为1");
        assertEquals(bindCode, updatedUser.getBindCode(), "用户绑定协议号应匹配");

        // 7. 查询绑卡状态
        System.out.println("=== 6. 查询绑卡状态 ===");
        MvcResult statusResult = mockMvc.perform(get("/api/user/bindStatus")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        String statusBody = statusResult.getResponse().getContentAsString();
        System.out.println("绑卡状态响应: " + statusBody);
        assertTrue(statusBody.contains("bindCode"), "绑卡状态应包含bindCode");

        System.out.println("=== 银行绑定全流程测试通过 ===");
    }

    @Test
    void testBankBindWithInvalidSign() throws Exception {
        User user = new User();
        user.setMobile(randomMobile());
        user.setName(randomChineseName());
        user.setNickName(user.getName());
        user.setPassword("123456");
        user.setUserType(1);
        user.setStatus(1);
        user.setIdCard(randomIdCard());
        userService.save(user);
        userId = user.getId();

        String token = TokenUtil.generateMerchantToken(userId.toString());

        System.out.println("=== 签名校验失败测试 ===");
        MvcResult result = mockMvc.perform(post("/api/core/userBind/notify")
                        .param("agentId", "999888")
                        .param("agentUserId", user.getMobile())
                        .param("personalName", user.getName())
                        .param("mobile", user.getMobile())
                        .param("idCard", user.getIdCard())
                        .param("bindCode", "BIND_ERROR")
                        .param("status", "1")
                        .param("bankNo", bankCard)
                        .param("sign", "invalidsign123"))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        System.out.println("签名错误响应: " + response);
        assertEquals("sign error", response, "签名错误应返回sign error");

        System.out.println("=== 签名校验失败测试通过 ===");
    }
}