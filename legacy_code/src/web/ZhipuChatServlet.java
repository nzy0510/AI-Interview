package web;

import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
@WebServlet("/chat")
public class ZhipuChatServlet extends HttpServlet {
    // 替换成你的智谱 API Key
    private static final String ZHIPU_API_KEY = "49119d9a29f1458c9391ab3d382a80ad.loEqOpMepZym8kvb";
    private static final String ZHIPU_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    /**
     * 核心方法：修复超时问题后的智谱 AI 调用逻辑
     */
    private String callZhipuAI(String prompt, String jobType) throws IOException {
        // 拼接岗位相关的提示词
        String finalPrompt = "你是" + jobType + "岗位的面试助手，专业回答以下问题：" + prompt;
        System.out.println("【调试】发送的提示词：" + finalPrompt); // 控制台打印，方便排查

        // 关键：强制使用 TLS 1.2/1.3（解决 HTTPS 握手兼容问题）
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");

        // 1. 创建 HTTP 连接（关闭缓存+允许重定向，对齐 Python 逻辑）
        URL url = new URL(ZHIPU_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setUseCaches(false); // 关闭缓存（Java 默认开启，Python 关闭）
        conn.setInstanceFollowRedirects(true); // 允许重定向
        conn.setDoOutput(true); // 允许发送请求体
        conn.setDoInput(true); // 允许读取响应

        // 2. 请求头（补充 User-Agent，避免被服务器拦截）
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + ZHIPU_API_KEY);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        // 3. 延长超时时间（给 HTTPS 握手和响应留足够时间）
        conn.setConnectTimeout(20000); // 连接超时 20 秒
        conn.setReadTimeout(40000);    // 读取超时 40 秒

        // 4. 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "glm-4");
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", finalPrompt);
        requestBody.put("messages", new JSONArray().put(message));

        // 5. 发送请求体
        try (OutputStream os = conn.getOutputStream()) {
            byte[] requestBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(requestBytes);
            os.flush();
        }

        // 6. 读取响应（打印状态码，方便定位问题）
        int responseCode = conn.getResponseCode();
        System.out.println("【调试】响应状态码：" + responseCode);
        if (responseCode == 200) {
            // 读取成功响应
            try (BufferedReader br = new BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder respSb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    respSb.append(line);
                }
                System.out.println("【调试】AI 响应内容：" + respSb); // 打印完整响应
                // 解析 AI 回复
                JSONObject respJson = new JSONObject(respSb.toString());
                return respJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            }
        } else {
            // 读取错误信息
            try (BufferedReader br = new BufferedReader(
                    new java.io.InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorSb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    errorSb.append(line);
                }
                return "API 调用失败（状态码：" + responseCode + "）：" + errorSb;
            }
        }
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        // 1. 获取岗位和用户问题
        String jobType = req.getParameter("selectedJob");
        String prompt = req.getParameter("prompt");

        // 2. 参数校验
        if (jobType == null || jobType.trim().isEmpty()) {
            resp.getWriter().write(new JSONObject(new Result("fail", "请先选择面试岗位！")).toString());
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            resp.getWriter().write(new JSONObject(new Result("fail", "请输入面试问题！")).toString());
            return;
        }

        // 3. 调用 AI 并返回结果
        try {
            String aiReply = callZhipuAI(prompt, jobType);
            resp.getWriter().write(new JSONObject(new Result("success", aiReply)).toString());
        } catch (Exception e) {
            resp.getWriter().write(new JSONObject(new Result("fail", "调用异常：" + e.getMessage())).toString());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    // 统一响应类
    record Result(String code, String data) {
    }
}