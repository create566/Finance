package com.example.demo;

import com.example.demo.client.SMSApiSmsClient;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.TokenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 注册 -> 验证码登录 -> 密码登录 -> token 全链路
 * 走 MockMvc 真实 HTTP 通道，JwtAuthInterceptor 真实生效
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenFlowTest {

    @Autowired
    private MockMvc mockMvc;

    /** 测试专用假短信服务：模拟发送验证码，返回固定验证码 123456 */
    @TestConfiguration
    static class MockSmsConfig {

        @Bean
        public static BeanFactoryPostProcessor removeFeignClients() {
            return factory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
                for (String name : registry.getBeanDefinitionNames()) {
                    BeanDefinition bd = registry.getBeanDefinition(name);
                    String cls = bd.getBeanClassName();
                    if (cls != null && cls.contains("FeignClientFactoryBean")) {
                        registry.removeBeanDefinition(name);
                    }
                }
            };
        }

        @Bean
        @org.springframework.context.annotation.Primary
        public SMSApiSmsClient smsApiSmsClient() {
            return new SMSApiSmsClient() {
                @Override
                public PccAjaxResult sendCode(String mobile) {
                    return new PccAjaxResult(200, "验证码已发送", "123456");
                }

                @Override
                public PccAjaxResult checkCode(String mobile, String code) {
                    boolean valid = "123456".equals(code);
                    return new PccAjaxResult(200, valid ? "验证码正确" : "验证码错误", valid);
                }

                @Override
                public PccAjaxResult sendNotify(String mobile, String content) {
                    return new PccAjaxResult(200, "发送成功");
                }
            };
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MOBILE = "198" + System.currentTimeMillis() % 100000000L;
    private static final String PASSWORD = "123456";
    private static String token;

    private static String body(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    void sendCode() throws Exception {
        System.out.println("=== 1. 发送验证码 ===");
        MvcResult result = mockMvc.perform(post("/api/user/sendCode")
                        .param("mobile", MOBILE))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        System.out.println("响应体: " + respBody);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "发送验证码应返回 code=200: " + respBody);
    }

    @Test
    @Order(2)
    void register() throws Exception {
        System.out.println("=== 2. 注册（手机号+密码+验证码） ===");
        String json = "{\"mobile\":\"" + MOBILE + "\",\"password\":\"" + PASSWORD + "\",\"passwordto\":\"" + PASSWORD + "\",\"code\":\"123456\"}";
        System.out.println("请求体: " + json);

        MvcResult result = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        System.out.println("响应体: " + respBody);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "注册应返回 code=200: " + respBody);
    }

    @Test
    @Order(3)
    void loginByCode() throws Exception {
        System.out.println("=== 3. 验证码登录 ===");
        String json = "{\"mobile\":\"" + MOBILE + "\",\"code\":\"123456\"}";
        System.out.println("请求体: " + json);

        MvcResult result = mockMvc.perform(post("/api/user/loginByCode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        System.out.println("响应体: " + respBody);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "验证码登录应返回 code=200: " + respBody);
        token = node.get("data").asText();
        assertTrue(token.startsWith("5grcs "), "token 应以 '5grcs ' 开头, 实际: " + token);
        System.out.println("token: " + token);
    }

    @Test
    @Order(4)
    void loginByPassword() throws Exception {
        System.out.println("=== 4. 密码登录 ===");
        String json = "{\"mobile\":\"" + MOBILE + "\",\"password\":\"" + PASSWORD + "\"}";
        System.out.println("请求体: " + json);

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        System.out.println("响应体: " + respBody);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "密码登录应返回 code=200: " + respBody);
        String token2 = node.get("data").asText();
        assertTrue(token2.startsWith("5grcs "), "token 应以 '5grcs ' 开头");
        System.out.println("token: " + token2);
    }

    @Test
    @Order(5)
    void tokenCanBeParsed() {
        System.out.println("=== 5. 解析 token ===");
        Map<String, String> info = TokenUtil.getMapInfoFromToken(token);
        assertFalse(info.isEmpty(), "token 解析失败");
        assertNotNull(info.get("token_userid"), "token 中应包含 token_userid");
        System.out.println("token_userid=" + info.get("token_userid"));
    }
}