<%--
  Created by IntelliJ IDEA.
  User: Zed
  Date: 2026/3/9
  Time: 22:16
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Title</title>
</head>
<body>
<style>
    form { width: 400px; margin: 0 auto; }
</style>
<form method="get" action="interviewAction">
    <!-- 页面标题 -->
    <div class="title">AI模拟面试 - 选择目标岗位</div>

    <!-- 岗位单选框（替换原来的性别选择） -->
    <div class="job-item">
        <input type="radio" name="jobType" value="javaBackend" id="javaBackend" required>
        <label for="javaBackend">Java后端工程师</label>
    </div>
    <div class="job-item">
        <input type="radio" name="jobType" value="webFrontend" id="webFrontend" required>
        <label for="webFrontend">Web前端工程师</label>
    </div>
    <p><input type="submit" value="开始面试"></p>
</form>

</body>
</html>
