package web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/loginAction")
public class LoginServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 设置请求和响应的字符编码，防止乱码
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html;charset=UTF-8");

        // 获取表单提交的用户名和密码
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        // ... (参数获取代码) ...

        // --- 模拟登录验证 ---
        // V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V
        // --> 数据库操作 3: 查询用户并验证凭据
        //    - 在这里，你需要连接数据库。
        //    - 执行一条 SQL SELECT 查询语句，根据用户名查找用户，例如:
        //      "SELECT password FROM users WHERE username = ?"
        //    - 如果没有找到用户，或用户输入的密码与数据库中的（解密/比对后的）密码不匹配，则登录失败。
        //    - 如果验证成功，则执行登录成功的逻辑。
        // ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
        if ("admin".equals(username) && "123".equals(password)) {
            // 登录成功
            // 创建或获取Session，并将用户信息存入Session，以便后续页面使用
            HttpSession session = req.getSession();
            session.setAttribute("loggedInUser", username);
            // 使用重定向（sendRedirect）跳转到目标岗位选择页面
            resp.sendRedirect("choose.jsp");
        } else {
            // 登录失败
            // 在请求中设置错误消息，然后转发回登录页面
            req.setAttribute("errorMessage", "用户名或密码错误，请重试！");
            req.getRequestDispatcher("login.jsp").forward(req, resp);
        }
    }
}
