package com.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.dto.LoginDTO;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.entity.User;
import com.interview.entity.UserPreference;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserPreferenceMapper;
import com.interview.service.EmailService;
import com.interview.service.UserService;
import com.interview.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserPreferenceMapper prefMapper;

    @Override
    public String login(LoginDTO loginDTO) {
        String loginIdentity = loginDTO.getUsername();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(User::getEmail, loginIdentity).or().eq(User::getUsername, loginIdentity));
        User user = this.getOne(wrapper);

        if (user == null) {
            throw new RuntimeException("邮箱或用户名不存在");
        }

        String md5Password = DigestUtil.md5Hex(loginDTO.getPassword());
        if (!user.getPassword().equals(md5Password) && !user.getPassword().equals(loginDTO.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());

        return jwtUtils.generateJwt(claims);
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        // 1. 验证邮箱验证码
        if (!emailService.verifyCode(registerDTO.getEmail(), registerDTO.getCode())) {
            throw new RuntimeException("验证码无效或已失效，请重新获取");
        }

        // 2. 检查用户名唯一
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (this.count(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 3. 检查邮箱唯一
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, registerDTO.getEmail());
        if (this.count(emailWrapper) > 0) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 4. 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(DigestUtil.md5Hex(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setNickname("User_" + System.currentTimeMillis() % 10000);

        this.save(user);
    }

    @Override
    public void sendVerificationCode(String email, String purpose) {
        emailService.sendVerificationCode(email, purpose);
    }

    @Override
    public void forgotPassword(String email) {
        // 检查邮箱是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        emailService.sendVerificationCode(email, "重置密码");
    }

    @Override
    public void resetPassword(ResetPasswordDTO resetDTO) {
        // 1. 验证验证码
        if (!emailService.verifyCode(resetDTO.getEmail(), resetDTO.getCode())) {
            throw new RuntimeException("验证码无效或已失效，请重新获取");
        }

        // 2. 查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, resetDTO.getEmail());
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }

        // 3. 更新密码
        user.setPassword(DigestUtil.md5Hex(resetDTO.getNewPassword()));
        this.updateById(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, String email) {
        User user = this.getById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        if (StrUtil.isNotBlank(nickname)) user.setNickname(nickname);
        if (StrUtil.isNotBlank(email)) {
            // 检查邮箱唯一
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getEmail, email).ne(User::getId, userId);
            if (this.count(wrapper) > 0) throw new RuntimeException("该邮箱已被使用");
            user.setEmail(email);
        }
        this.updateById(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = this.getById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        String oldMd5 = DigestUtil.md5Hex(oldPassword);
        if (!user.getPassword().equals(oldMd5) && !user.getPassword().equals(oldPassword)) {
            throw new RuntimeException("旧密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码至少6位");
        }
        user.setPassword(DigestUtil.md5Hex(newPassword));
        this.updateById(user);
    }

    @Override
    public UserPreference getPreference(Long userId) {
        var wrapper = new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId);
        UserPreference pref = prefMapper.selectOne(wrapper);
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(userId);
            pref.setDefaultMode("text");
            pref.setDifficultyLevel("mid");
        }
        return pref;
    }

    @Override
    public void updatePreference(Long userId, UserPreference pref) {
        var wrapper = new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId);
        UserPreference existing = prefMapper.selectOne(wrapper);
        UserPreference safePref = buildSafePreference(userId, pref, existing);
        if (existing != null) {
            safePref.setId(existing.getId());
            prefMapper.updateById(safePref);
        } else {
            prefMapper.insert(safePref);
        }
    }

    private UserPreference buildSafePreference(Long userId, UserPreference input, UserPreference existing) {
        UserPreference target = existing != null ? existing : new UserPreference();
        UserPreference source = input != null ? input : new UserPreference();

        target.setUserId(userId);
        target.setDefaultMode(validOrDefault(source.getDefaultMode(),
                existing != null ? existing.getDefaultMode() : "text",
                Set.of("text", "video")));
        if (source.getDefaultRole() != null) {
            target.setDefaultRole(source.getDefaultRole());
        }
        if (source.getFocusAreas() != null) {
            target.setFocusAreas(source.getFocusAreas());
        }
        target.setDifficultyLevel(validOrDefault(source.getDifficultyLevel(),
                existing != null ? existing.getDifficultyLevel() : "mid",
                Set.of("junior", "mid", "senior", "principal")));
        return target;
    }

    private String validOrDefault(String value, String fallback, Set<String> allowed) {
        if (allowed.contains(value)) return value;
        return StrUtil.isNotBlank(fallback) ? fallback : allowed.iterator().next();
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = this.getById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        user.setAvatar(avatarUrl);
        this.updateById(user);
    }

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = this.getById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/png")
                && !contentType.equals("image/jpeg")
                && !contentType.equals("image/webp"))) {
            throw new RuntimeException("仅支持 PNG / JPG / WebP 格式的头像");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new RuntimeException("头像文件不能超过 2MB");
        }

        try {
            String ext = contentType.equals("image/png") ? "png"
                    : contentType.equals("image/webp") ? "webp" : "jpg";
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;

            Path basePath = Paths.get(uploadDir);
            if (!basePath.isAbsolute()) {
                basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath);
            }
            Path uploadPath = basePath.resolve("avatars");
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(filename);
            file.transferTo(filePath.toFile());

            String avatarUrl = "/uploads/avatars/" + filename;
            user.setAvatar(avatarUrl);
            this.updateById(user);

            return avatarUrl;
        } catch (Exception e) {
            throw new RuntimeException("头像上传失败: " + e.getMessage());
        }
    }
}
