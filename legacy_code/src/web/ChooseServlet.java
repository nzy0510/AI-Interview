package web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/chooseAction")
public class ChooseServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 从 choose.jsp 表单获取用户选择的岗位
        String jobType = req.getParameter("selectedJob");

        // 打印到控制台，用于调试
        System.out.println("用户选择的岗位是: " + jobType);

// V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V V
        // --> 数据库操作 4 (未来扩展): 获取面试问题
        //    - 在这里，你可以根据用户选择的 `jobType`。
        //    - 连接数据库，并从一个 "questions" 表中查询与该岗位相关的所有面试题，例如:
        //      "SELECT * FROM questions WHERE job_category = ?"
        //    - 将查询到的问题列表存入 Session 或 Request，然后跳转到真正的面试页面。
        // ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^

        // 这里我们暂时只打印一条成功信息到页面上作为演示
        resp.setContentType("text/html;charset=UTF-8");
        req.setAttribute("selectedJob", jobType);

        // 🌟 关键2：跳转到 ZhipuChatServlet 的映射路径（/chat），而非 .java 文件
        // 注意：路径开头加 / 表示项目根路径，避免相对路径错误
        RequestDispatcher rd = req.getRequestDispatcher("/interview.jsp");
        rd.forward(req, resp);
    }
}
