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
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI模拟面试 - 选择目标岗位</title>
    <link rel="stylesheet" href="styles/choose.css">
</head>
<body>
<form method="post" action="chooseAction">
    <!-- 页面标题 -->
    <h1 class="title">AI模拟面试 - 选择目标岗位</h1>
    <fieldset>
        <legend>请选择您的目标岗位</legend>

        <div class="job-item">
            <input type="radio" name="jobType" value="javaBackend" id="javaBackend" required checked>
            <label for="javaBackend">Java后端工程师</label>
        </div>
        <div class="job-item">
            <input type="radio" name="jobType" value="webFrontend" id="webFrontend" required>
            <label for="webFrontend">Web前端工程师</label>
        </div>
        <div class="job-item">
            <input type="radio" name="jobType" value="dataScientist" id="dataScientist" required>
            <label for="dataScientist">数据科学家</label>
        </div>
        <div class="job-item">
            <input type="radio" name="jobType" value="aiEngineer" id="aiEngineer" required>
            <label for="aiEngineer">AI工程师</label>
        </div>
    </fieldset>

    <div class="form-actions">
        <input type="submit" value="开始面试">
    </div>

    <div class="back-link">
        <a href="index.jsp">返回首页</a>
    </div>
</form>

</body>
</html>
