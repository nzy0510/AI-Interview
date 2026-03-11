<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>面试助手 - <%= request.getAttribute("selectedJob") %></title>
</head>
<body>
<h1>你选择的岗位：<%= request.getAttribute("selectedJob") %></h1>
<form action="${pageContext.request.contextPath}/chat" method="post">
    <!-- 隐藏域传递岗位名称（传给 ZhipuChatServlet） -->
    <input type="hidden" name="selectedJob" value="<%= request.getAttribute("selectedJob") %>">
    <!-- 面试问题输入框 -->
    <label>请输入面试问题：</label>
    <textarea name="prompt" rows="5" cols="80" placeholder="比如：说说你对Spring Boot的理解？"></textarea><br><br>
    <button type="submit">获取AI回答</button>
</form>
</body>
</html>