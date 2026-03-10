package web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/registerAction")
public class RegisterServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 设置请求和响应的字符编码，防止乱码
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html;charset=UTF-8");

        // 获取表单提交的参数
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        // --- 基本的后端验证 ---
        // 1. 检查密码和确认密码是否一致
        if (password == null || !password.equals(confirmPassword)) {
            req.setAttribute("errorMessage", "两次输入的密码不一致，请重试！");
            req.getRequestDispatcher("reg.jsp").forward(req, resp);
            return; // 停止后续执行
        }

        // 2. 检查用户名或密码是否为空 (虽然前端有 'required'，但后端验证是必须的)
        if (username == null || username.trim().isEmpty() || password.isEmpty()) {
            req.setAttribute("errorMessage", "用户名和密码不能为空！");
            req.getRequestDispatcher("reg.jsp").forward(req, resp);
            return;
        }

        // --- 模拟将新用户保存到数据库 ---
        // V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V
        // --> 数据库操作 1: 检查用户名是否存在
        //    - 在这里，你需要连接数据库。
        //    - 执行一条 SQL SELECT 查询语句，例如:
        //      "SELECT COUNT(*) FROM users WHERE username = ?"
        //    - 检查查询结果。如果结果大于0，说明用户名已被注册，应返回错误信息给用户。

        // --> 数据库操作 2: 插入新用户
        //    - 如果用户名未被注册，你将执行一条 SQL INSERT 语句，例如:
        //      "INSERT INTO users (username, password) VALUES (?, ?)"
        //    - 注意：在实际操作中，密码必须经过哈希加密处理后再存入数据库。
        // ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
        System.out.println("新用户注册成功: username=" + username);

        // --- 注册成功后，重定向到登录页面，并附带一个成功提示 ---
        resp.sendRedirect("login.jsp?reg=success");
    }
}