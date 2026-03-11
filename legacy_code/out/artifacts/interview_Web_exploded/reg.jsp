<%--
  Created by IntelliJ IDEA.
  User: Zed
  Date: 2026/3/9
  Time: 22:19
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>用户注册</title>
    <link rel="stylesheet" href="styles/reg.css">
</head>
<body>
<form method="post" action="registerAction">
    <h1 class="form-title">用户注册</h1>

    <%-- 用于显示注册失败时的错误信息 --%>
    <%
        String errorMessage = (String) request.getAttribute("errorMessage");
        if (errorMessage != null && !errorMessage.isEmpty()) {
    %>
    <p style="color: red; text-align: center; font-weight: bold;"><%= errorMessage %></p>
    <%
        }
    %>

    <fieldset>
        <legend>请填写您的信息</legend>

        <div class="form-group">
            <label for="username">用户名:</label>
            <input type="text" id="username" name="username" required placeholder="请输入用户名">
        </div>

        <div class="form-group">
            <label for="password">密码:</label>
            <input type="password" id="password" name="password" required placeholder="请输入密码">
        </div>

        <div class="form-group">
            <label for="confirmPassword">确认密码:</label>
            <input type="password" id="confirmPassword" name="confirmPassword" required placeholder="请再次输入密码">
        </div>

    </fieldset>

    <div class="form-actions">
        <input type="submit" value="注册">
    </div>

    <div class="login-link">
        已有账号？<a href="login.jsp">立即登录</a>
    </div>
</form>

</body>
</html>
