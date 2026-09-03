package com.example.demo;

import com.example.demo.utils.TokenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * 验证 token 全链路：注册 -> 登录签发 token -> 解析 token -> 携带 token 访问受保护接口
 * 走 MockMvc 真实 HTTP 通道，JwtAuthInterceptor 真实生效
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenFlowTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MOBILE = "198" + System.currentTimeMillis() % 100000000L;
    private static final String PASSWORD = "123456";
    private static String token;

    /** 按 UTF-8 取响应体，避免中文乱码 */
    private static String body(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    void register() throws Exception {
        String json = "{\"mobile\":\"" + MOBILE + "\",\"password\":\"" + PASSWORD + "\",\"passwordto\":\"" + PASSWORD + "\"}";
        System.out.println("=== 1. 注册 请求信息 ===");
        System.out.println("POST /api/user/register");
        System.out.println("请求体: " + json);

        MvcResult result = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "注册应返回 code=200: " + respBody);
        System.out.println("响应体: " + respBody);
    }

    @Test
    @Order(2)
    void loginAndGetToken() throws Exception {
        String json = "{\"mobile\":\"" + MOBILE + "\",\"password\":\"" + PASSWORD + "\"}";
        System.out.println("=== 2. 登录 请求信息 ===");
        System.out.println("POST /api/user/login");
        System.out.println("请求体: " + json);

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "登录应返回 code=200: " + respBody);
        token = node.get("data").asText();
        assertTrue(token.startsWith("5grcs "), "token 应以 '5grcs ' 开头, 实际: " + token);
        System.out.println("响应体: " + respBody);
        System.out.println("=== 完整 token ===");
        System.out.println(token);
    }

    @Test
    @Order(3)
    void tokenCanBeParsed() {
        Map<String, String> info = TokenUtil.getMapInfoFromToken(token);
        assertFalse(info.isEmpty(), "token 解析失败");
        assertNotNull(info.get("token_userid"), "token 中应包含 token_userid");
        System.out.println("=== 3. 解析 token ===");
        System.out.println("token_userid=" + info.get("token_userid"));
    }

    @Test
    @Order(4)
    void protectedApiWithToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/core/borrowInfo/auth/getBorrowInfoStatus")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        String respBody = body(result);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(200, node.get("code").asInt(), "带 token 访问应返回 code=200: " + respBody);
        System.out.println("=== 4. 带 token 访问受保护接口 ===");
        System.out.println("Authorization: " + token.substring(0, 36) + "...");
        System.out.println("响应体: " + respBody);
    }

    @Test
    @Order(5)
    void protectedApiWithoutToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/core/borrowInfo/auth/getBorrowInfoStatus"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String respBody = body(result);
        JsonNode node = MAPPER.readTree(respBody);
        assertEquals(401, node.get("code").asInt(), "无 token 应被拦截返回 401");
        System.out.println("=== 5. 无 token 访问受保护接口 ===");
        System.out.println("响应体: " + respBody);
    }
}
