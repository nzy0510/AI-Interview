# 昵称显示、头像上传、退出登录 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dashboard 显示昵称（动态更新）、Settings 支持头像上传、Settings 支持退出登录

**Architecture:** 三个独立功能：前端 Dashboard 从 API 加载昵称替代 JWT 用户名 + localStorage 缓存实现动态更新；后端新增头像上传 API + 静态资源映射 + 前端头像 UI；前端 `auth.js` 新增 `logout()` 清除登录态。JWT 无状态，登出仅客户端清除。

**Tech Stack:** Vue 3 + Element Plus + Spring Boot + MyBatis-Plus + JUnit 5 + Mockito

---

### Task 1: Dashboard 显示昵称替代用户名

**Files:**
- Modify: `E:\Java-web\interview\frontend\src\components\dashboard\DashboardHome.vue:318,7`
- Modify: `E:\Java-web\interview\frontend\src\utils\auth.js` (末尾追加)
- Modify: `E:\Java-web\interview\frontend\src\views\Settings.vue:140-141` (保存资料后同步缓存)

- [ ] **Step 1: auth.js 新增 getNickname / setNickname 工具函数**

在 `src/utils/auth.js` 末尾追加：

```js
/**
 * 获取缓存的昵称（用于跨页面同步）
 */
export function getNickname() {
  return localStorage.getItem('cached_nickname') || null
}

/**
 * 缓存昵称（Settings 保存后调用）
 */
export function setNickname(nickname) {
  if (nickname) {
    localStorage.setItem('cached_nickname', nickname)
  } else {
    localStorage.removeItem('cached_nickname')
  }
}

/**
 * 清除所有登录态（退出登录时调用）
 */
export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('cached_nickname')
  // 清除所有以 userKey 格式缓存的用户数据
  const userId = getUserId()
  if (userId) {
    Object.keys(localStorage).forEach(key => {
      if (key.endsWith(`_${userId}`)) localStorage.removeItem(key)
    })
  }
}
```

- [ ] **Step 2: 运行现有测试确认基线**

Run: `cd E:\Java-web\interview\backend && mvn test -pl . -Dtest=UserServiceTest -DfailIfNoTests=false -q`
Expected: 5 tests PASS

- [ ] **Step 3: DashboardHome.vue 从 API 加载昵称并显示**

修改 `DashboardHome.vue` 第 318 行附近：

```js
// 替换:
// const username = ref(getUsername() || '用户')

// 为:
import { getUsername, getNickname, setNickname, userKey } from '@/utils/auth'
import { getCurrentUserAPI } from '@/api/user'

const displayName = ref(getNickname() || getUsername() || '用户')
```

修改模板第 7 行：

```html
<!-- 替换: -->
<h1>{{ username }}</h1>
<!-- 为: -->
<h1>{{ displayName }}</h1>
```

在 `onMounted` 中已有 `loadPreference()` 调用链，追加昵称加载逻辑。在 script setup 中添加：

```js
const loadNickname = async () => {
  // 优先使用缓存
  const cached = getNickname()
  if (cached) {
    displayName.value = cached
    return
  }
  // 从 API 加载
  try {
    const user = await getCurrentUserAPI()
    if (user?.nickname) {
      displayName.value = user.nickname
      setNickname(user.nickname)
    } else if (user?.username) {
      displayName.value = user.username
    }
  } catch { /* fallback to getUsername() already set */ }
}
```

修改 `onMounted` 调用，在 `Promise.all` 中增加 `loadNickname()`：

```js
onMounted(async () => {
  await Promise.all([checkExistingResume(), loadHistory(), loadPreference(), loadNickname()])
  loadMentor()
})
```

- [ ] **Step 4: Settings.vue 保存资料后同步昵称缓存**

修改 `Settings.vue` 的 `saveProfile` 函数（第 137-145 行），成功后同步缓存：

```js
const saveProfile = async () => {
  savingProfile.value = true
  try {
    await updateProfileAPI({ nickname: profile.nickname, email: profile.email })
    // 同步昵称缓存，Dashboard 下次加载时自动显示新昵称
    import('@/utils/auth').then(({ setNickname }) => setNickname(profile.nickname))
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { savingProfile.value = false }
}
```

- [ ] **Step 5: 验证**

启动前端 dev server，验证：
1. Dashboard hero headline 显示昵称而非用户名
2. 去 Settings 修改昵称并保存
3. 回到 Dashboard，刷新页面，昵称已更新

- [ ] **Step 6: Commit**

```bash
git -C /e/Java-web/interview add frontend/src/components/dashboard/DashboardHome.vue frontend/src/utils/auth.js frontend/src/views/Settings.vue
git -C /e/Java-web/interview commit -m "feat(frontend): Dashboard 显示昵称替代用户名，Settings 保存后同步缓存"
```

---

### Task 2: 后端头像上传 API

**Files:**
- Create: `E:\Java-web\interview\backend\src\main\java\com\interview\controller\UserController.java` (追加方法)
- Modify: `E:\Java-web\interview\backend\src\main\java\com\interview\service\UserService.java` (追加方法签名)
- Modify: `E:\Java-web\interview\backend\src\main\java\com\interview\service\impl\UserServiceImpl.java` (追加实现)
- Modify: `E:\Java-web\interview\backend\src\main\java\com\interview\AiInterviewApplication.java` (追加静态资源配置)
- Test: `E:\Java-web\interview\backend\src\test\java\com\interview\controller\UserControllerTest.java` (新建)
- Test: `E:\Java-web\interview\backend\src\test\java\com\interview\service\UserServiceTest.java` (追加测试)

- [ ] **Step 1: 写失败测试 — UserServiceTest 追加 updateAvatar 测试**

在 `UserServiceTest.java` 末尾追加：

```java
@Test
@DisplayName("更新用户头像链接")
void shouldUpdateAvatar() {
    User user = createUser();
    when(userMapper.selectById(1L)).thenReturn(user);
    when(userMapper.updateById(any(User.class))).thenReturn(1);

    userService.updateAvatar(1L, "/uploads/avatars/1_abc123.png");

    assertThat(user.getAvatar()).isEqualTo("/uploads/avatars/1_abc123.png");
    verify(userMapper).updateById(user);
}

@Test
@DisplayName("更新头像：用户不存在时抛异常")
void shouldThrowWhenUserNotFoundForAvatar() {
    when(userMapper.selectById(999L)).thenReturn(null);

    assertThatThrownBy(() -> userService.updateAvatar(999L, "/uploads/avatars/x.png"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("用户不存在");
}
```

Run: `cd E:\Java-web\interview\backend && mvn test -Dtest=UserServiceTest -DfailIfNoTests=false -q`
Expected: 2 tests FAIL (方法未定义)

- [ ] **Step 2: UserService 接口追加方法签名**

在 `UserService.java` 末尾（`}` 前）追加：

```java
void updateAvatar(Long userId, String avatarUrl);
```

- [ ] **Step 3: UserServiceImpl 实现 updateAvatar**

在 `UserServiceImpl.java` 末尾（`}` 前）追加：

```java
@Override
public void updateAvatar(Long userId, String avatarUrl) {
    User user = this.getById(userId);
    if (user == null) throw new RuntimeException("用户不存在");
    user.setAvatar(avatarUrl);
    this.updateById(user);
}
```

Run: `cd E:\Java-web\interview\backend && mvn test -Dtest=UserServiceTest -DfailIfNoTests=false -q`
Expected: 7 tests PASS

- [ ] **Step 4: UserController 追加头像上传端点**

在 `UserController.java` 末尾（`}` 前）追加：

```java
/** 上传用户头像 */
@PostMapping("/avatar")
public Result<Map<String, String>> uploadAvatar(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request) {
    Long userId = (Long) request.getAttribute("currentUserId");
    String avatarUrl = userService.uploadAvatar(userId, file);
    return Result.success(Map.of("avatarUrl", avatarUrl));
}
```

需要在文件头部追加 import：

```java
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
```

- [ ] **Step 5: UserService 接口追加 uploadAvatar 方法签名**

在 `UserService.java` 末尾追加：

```java
String uploadAvatar(Long userId, MultipartFile file);
```

需要在文件头部追加 import：

```java
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 6: UserServiceImpl 实现 uploadAvatar（含文件存储）**

在 `UserServiceImpl.java` 末尾追加：

```java
@Value("${app.upload-dir:uploads}")
private String uploadDir;

@Override
public String uploadAvatar(Long userId, MultipartFile file) {
    User user = this.getById(userId);
    if (user == null) throw new RuntimeException("用户不存在");

    // 校验文件类型
    String contentType = file.getContentType();
    if (contentType == null || (!contentType.equals("image/png")
            && !contentType.equals("image/jpeg")
            && !contentType.equals("image/webp"))) {
        throw new RuntimeException("仅支持 PNG / JPG / WebP 格式的头像");
    }

    // 校验大小 (最大 2MB)
    if (file.getSize() > 2 * 1024 * 1024) {
        throw new RuntimeException("头像文件不能超过 2MB");
    }

    try {
        // 生成唯一文件名
        String ext = contentType.equals("image/png") ? "png"
                : contentType.equals("image/webp") ? "webp" : "jpg";
        String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;

        // 确保目录存在
        Path uploadPath = Paths.get(uploadDir, "avatars");
        Files.createDirectories(uploadPath);

        // 写入文件
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

        // 更新数据库
        String avatarUrl = "/uploads/avatars/" + filename;
        user.setAvatar(avatarUrl);
        this.updateById(user);

        return avatarUrl;
    } catch (Exception e) {
        throw new RuntimeException("头像上传失败: " + e.getMessage());
    }
}
```

需要在文件头部追加 import：

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
```

- [ ] **Step 7: AiInterviewApplication 追加静态资源映射**

在 `AiInterviewApplication.java` 的类体中追加：

```java
@Bean
public WebMvcConfigurer avatarStaticResourceConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations("file:uploads/");
        }
    };
}
```

需要在文件头部追加 import：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
```

- [ ] **Step 8: 运行后端全部测试**

Run: `cd E:\Java-web\interview\backend && mvn test -q`
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git -C /e/Java-web/interview add backend/src/main/java/com/interview/controller/UserController.java backend/src/main/java/com/interview/service/UserService.java backend/src/main/java/com/interview/service/impl/UserServiceImpl.java backend/src/main/java/com/interview/AiInterviewApplication.java backend/src/test/java/com/interview/service/UserServiceTest.java
git -C /e/Java-web/interview commit -m "feat(backend): 新增头像上传 API 与静态资源映射"
```

---

### Task 3: Settings 前端 — 头像上传 + 退出登录

**Files:**
- Modify: `E:\Java-web\interview\frontend\src\views\Settings.vue`
- Modify: `E:\Java-web\interview\frontend\src\utils\auth.js` (已完成，Task 1 已追加 logout)
- Modify: `E:\Java-web\interview\frontend\src\api\user.js` (追加 uploadAvatarAPI)

- [ ] **Step 1: user.js API 追加 uploadAvatarAPI**

在 `src/api/user.js` 末尾追加：

```js
export const uploadAvatarAPI = (formData) => {
    return request({
        url: '/user/avatar',
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}
```

- [ ] **Step 2: Settings.vue 模板 — 账号信息区添加头像上传 UI**

在 `Settings.vue` 模板的账号信息 section（`section-shell` 第一个）中，`settings-grid` 的第一个 `settings-block` 顶部（`<el-form>` 之前）插入：

```html
<div class="avatar-section">
  <el-upload
    class="avatar-uploader"
    :action="avatarUploadUrl"
    :headers="uploadHeaders"
    :show-file-list="false"
    :before-upload="beforeAvatarUpload"
    :on-success="handleAvatarSuccess"
    :on-error="handleAvatarError"
    accept="image/png,image/jpeg,image/webp"
  >
    <el-avatar v-if="profile.avatar" :src="profile.avatar" :size="72" />
    <el-icon v-else class="avatar-uploader-icon" :size="72"><UserFilled /></el-icon>
  </el-upload>
  <p class="avatar-hint">点击上传头像，支持 PNG / JPG / WebP，最大 2MB</p>
</div>
```

- [ ] **Step 3: Settings.vue 模板 — 账号信息区添加退出登录按钮**

在 `section-head` 的 `<el-button type="primary" size="small" :loading="savingProfile" @click="saveProfile">保存资料</el-button>` 后面追加：

```html
<el-button type="danger" size="small" plain @click="handleLogout">退出登录</el-button>
```

- [ ] **Step 4: Settings.vue script — 追加头像上传和退出登录逻辑**

在 `<script setup>` 中追加 import：

```js
import { UserFilled } from '@element-plus/icons-vue'
import { uploadAvatarAPI } from '@/api/user'
import { logout } from '@/utils/auth'
```

在 script 中追加响应式状态和方法：

```js
const avatarUploadUrl = `${import.meta.env.VITE_API_BASE_URL || ''}/api/user/avatar`
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }

const beforeAvatarUpload = (file) => {
  const isImage = ['image/png', 'image/jpeg', 'image/webp'].includes(file.type)
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('仅支持 PNG / JPG / WebP 格式！'); return false }
  if (!isLt2M) { ElMessage.error('头像文件不能超过 2MB！'); return false }
  return true
}

const handleAvatarSuccess = (response) => {
  if (response?.code === 200 && response.data?.avatarUrl) {
    profile.avatar = response.data.avatarUrl
    ElMessage.success('头像已更新')
  } else {
    ElMessage.error(response?.msg || '上传失败')
  }
}

const handleAvatarError = () => {
  ElMessage.error('头像上传失败，请重试')
}

const handleLogout = () => {
  logout()
  router.push('/login')
}
```

- [ ] **Step 5: Settings.vue CSS — 追加头像区域样式**

在 `<style scoped>` 末尾追加：

```css
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.avatar-uploader :deep(.el-upload) {
  border: 2px dashed rgba(69, 70, 82, 0.15);
  border-radius: 50%;
  cursor: pointer;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader :deep(.el-upload:hover) {
  border-color: #3a388b;
}

.avatar-uploader-icon {
  color: #b0aea5;
}

.avatar-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
```

- [ ] **Step 6: 验证**

启动前端 dev server + 后端：
1. Settings 页面显示头像上传区域（圆形虚线框）
2. 点击上传头像文件，头像显示更新
3. 刷新页面，头像保持（从 API 加载）
4. 点击"退出登录"，跳转到 `/login`，token 被清除
5. 再次访问 `/`，被路由守卫拦截跳转 `/login`

- [ ] **Step 7: Commit**

```bash
git -C /e/Java-web/interview add frontend/src/views/Settings.vue frontend/src/api/user.js frontend/src/utils/auth.js
git -C /e/Java-web/interview commit -m "feat(frontend): Settings 新增头像上传与退出登录功能"
```

---

### Task 4: 集成验证 + 后端测试完善

**Files:**
- Modify: `E:\Java-web\interview\backend\src\test\java\com\interview\service\UserServiceTest.java`

- [ ] **Step 1: 追加 uploadAvatar 文件存储测试**

在 `UserServiceTest.java` 末尾追加：

```java
@Test
@DisplayName("上传头像：文件类型不合法时抛异常")
void shouldRejectInvalidAvatarType() {
    User user = createUser();
    when(userMapper.selectById(1L)).thenReturn(user);
    org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
    when(file.getContentType()).thenReturn("image/gif");

    assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("仅支持");

    verify(userMapper, never()).updateById(any());
}

@Test
@DisplayName("上传头像：文件过大时抛异常")
void shouldRejectOversizedAvatar() {
    User user = createUser();
    when(userMapper.selectById(1L)).thenReturn(user);
    org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getSize()).thenReturn(5 * 1024 * 1024L);

    assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("2MB");

    verify(userMapper, never()).updateById(any());
}
```

需要在文件头部追加 import：

```java
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
```

- [ ] **Step 2: 运行全部测试确认**

Run: `cd E:\Java-web\interview\backend && mvn test -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git -C /e/Java-web/interview add backend/src/test/java/com/interview/service/UserServiceTest.java
git -C /e/Java-web/interview commit -m "test(backend): 补充头像上传边界条件测试"
```

---
