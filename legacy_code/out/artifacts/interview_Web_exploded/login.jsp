<%--
  Created by IntelliJ IDEA.
  User: Zed
  Date: 2024/03/10
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>用户登录</title>
    <link rel="stylesheet" href="styles/reg.css"> <%-- 复用注册页面的样式 --%>
</head>
<body>
<form method="post" action="loginAction">
    <h1 class="form-title">用户登录</h1>

    <%-- 用于显示注册成功后的提示信息 --%>
    <%
        String regSuccess = request.getParameter("reg");
        if ("success".equals(regSuccess)) {
    %>
    <p style="color: green; text-align: center; font-weight: bold;">注册成功，请登录！</p>
    <%
        }
    %>

    <%-- 用于显示登录失败时的错误信息 --%>
    <%
        String errorMessage = (String) request.getAttribute("errorMessage");
        if (errorMessage != null && !errorMessage.isEmpty()) {
    %>
    <p style="color: red; text-align: center; font-weight: bold;"><%= errorMessage %></p>
    <%
        }
    %>

    <fieldset>
        <legend>请输入您的凭据</legend>

        <div class="form-group">
            <label for="username">用户名:</label>
            <input type="text" id="username" name="username" required placeholder="请输入用户名">
        </div>

        <div class="form-group">
            <label for="password">密码:</label>
            <input type="password" id="password" name="password" required placeholder="请输入密码">
        </div>
    </fieldset>

    <div class="form-actions">
        <input type="submit" value="登录">
    </div>

    <div class="login-link">
        还没有账号？<a href="reg.jsp">立即注册</a>
    </div>
</form>
</body>
</html>