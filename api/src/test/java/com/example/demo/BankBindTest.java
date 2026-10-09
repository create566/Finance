package com.example.demo;

import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.entity.Contract;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.InvestorAuthStatusEnum;
import com.example.demo.service.ContractService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.LoginUserContext;
import com.wwfinance.common.utils.TokenUtil;
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 银行绑定全流程测试
 * 模拟：注册用户 → 登录 → 发起绑卡 → 银行异步回调
 *
 * <p><b>2026-10-08 现代化</b>：本用例写于「绑卡还没加前置闸门」的年代，此后生产代码陆续加了
 * 三道门，用例没跟着改，长期恒失败。本次一次性补齐：</p>
 * <ol>
 *   <li><b>登录态</b>：拦截器要求请求头 token 与 Redis {@code login:token:user:<id>} 的值
 *       <b>完全相等</b>，只自签 JWT 过不了 → 测试里补写 Redis（原先恒 401）。</li>
 *   <li><b>实名认证闸门</b>：{@code buildBindForm} 要求借款人或投资人任一 AUTHED
 *       → 测试用户置 {@code investAuthStatus = AUTHED}。</li>
 *   <li><b>短信验证码闸门</b>：{@code buildBindForm} 会 Feign 调 service-sms 校验 code，
 *       且 {@code code == null} 直接拒 → 用 {@link MockBean} 顶掉短信客户端，并带上 code 参数。</li>
 *   <li><b>资金存管协议闸门（P9-61，本次新增）</b>：{@code BankBindController.bind} 现要求
 *       用户先签署《资金存管协议》（绑卡即开立存管账户）→ 测试里先
 *       {@code getOrCreateUserAgreement(DEPOSIT)} + {@code sign()} 把它签掉。</li>
 * </ol>
 * <p>另：签名失败用例的期望值由 {@code sign error} 订正为 {@code fail}（生产端早已改）。</p>
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

    /** 真实合同服务：用于在绑卡前把《资金存管协议》签掉（P9-61 闸门） */
    @Autowired
    private ContractService contractService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 顶掉真实 Feign 短信客户端：绑卡前置校验要调 service-sms 验码，测试环境造不出真验证码 */
    @MockBean
    private SMSApiSmsClient smsApiSmsClient;

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
        // 短信校验一律放行（真实 Feign 会打 service-sms:8120，测试里没有真验证码）
        when(smsApiSmsClient.checkCode(anyString(), anyString()))
                .thenReturn(new PccAjaxResult(200, "校验通过", Boolean.TRUE));
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
        // buildBindForm 的前置闸门：借款人或投资人任一实名通过即可（见 UserBindServiceImpl:128-133）
        user.setInvestAuthStatus(InvestorAuthStatusEnum.AUTHED.getCode());
        userService.save(user);
        userId = user.getId();
        assertNotNull(userId, "用户ID不应为空");

        // 2. 生成token
        token = TokenUtil.generateMerchantToken(userId.toString());
        assertTrue(token.startsWith("5grcs "), "token应以 '5grcs ' 开头");

        // 2.1 把 token 写进 Redis 登录态（2026-10-08 修）：
        //     拦截器（JwtAuthInterceptor）要求请求头 token 与 login:token:user:<id> 的
        //     值【完全相等】，只自签一个 JWT 是过不了的 —— 本用例原先正是漏了这一步，
        //     导致第 3 步 /api/user/bind 恒 401。写进去才对得上。
        stringRedisTemplate.opsForValue()
                .set("login:token:user:" + userId, token, java.time.Duration.ofHours(2));

        // 2.2 【P9-61】绑卡即开立资金存管账户，须先签署《资金存管协议》。
        //     这是本用例写完之后才加的第 4 道闸门：不先签掉，第 3 步拿到的会是
        //     一张「请先签署《资金存管协议》」的 HTML 错误页，而不是绑卡表单。
        Contract deposit = contractService.getOrCreateUserAgreement(ContractTypeEnum.DEPOSIT, userId);
        assertNotNull(deposit, "《资金存管协议》模板应已配置（sql/contract_service_deposit.sql）");
        contractService.sign(deposit.getId(), userId,
                // 1x1 透明 PNG：只要能被 Base64 解码且非空即可，内容不参与验签
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                "127.0.0.1", "junit-BankBindTest");

        // 3. 发起绑卡请求
        System.out.println("=== 3. 发起绑卡 ===");
        MvcResult bindResult = mockMvc.perform(get("/api/user/bind")
                        .param("code", "123456")
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

        // 清理：移除本用例写入的登录态，避免残留 token 污染 Redis
        stringRedisTemplate.delete("login:token:user:" + userId);

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
        // 2026-10-08 修：生产端 BankNotifyController 早先已从 "sign error" 改为
        // 统一返回 "success" / "fail"（对账日志那次重写），测试没跟上 —— 期望值订正为 "fail"。
        assertEquals("fail", response, "签名错误应返回fail");

        System.out.println("=== 签名校验失败测试通过 ===");
    }
}