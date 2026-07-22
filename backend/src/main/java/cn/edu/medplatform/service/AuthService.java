package cn.edu.medplatform.service;

import cn.edu.medplatform.entity.*;
import cn.edu.medplatform.repository.*;
import cn.edu.medplatform.security.JwtTokenProvider;
import cn.edu.medplatform.common.*;
import cn.edu.medplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PermissionRepository permRepo;
    private final AuditService auditService;
    private final JwtTokenProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleService userRoleService;

    @Value("${app.login.max-attempts:5}") private int maxAttempts;
    @Value("${app.login.lock-minutes:15}") private int lockMinutes;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        User user = userRepo.findByUsername(username);
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        if (!user.getEnabled()) throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已禁用");

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= maxAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                user.setFailedAttempts(0);
            }
            userRepo.save(user);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        user.setFailedAttempts(0);
        userRepo.save(user);

        List<String> roles = getRoles(user.getId());
        String access = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refresh = jwtProvider.generateRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refresh);

        auditService.log(user.getId(), user.getUsername(), roles.isEmpty() ? "" : roles.get(0), "LOGIN", "USER", String.valueOf(user.getId()), null, null);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", access);
        result.put("refreshToken", refresh);
        result.put("expiresIn", 7200);
        result.put("user", buildUserInfo(user, roles));
        return result;
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String realName, String phone, String gender, String birthDate) {
        if (userRepo.findByUsername(username) != null)
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "用户名已存在");
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setGender(gender);
        if (birthDate != null) user.setBirthDate(java.time.LocalDate.parse(birthDate));
        userRepo.save(user);

        Role patientRole = roleRepo.findByCode("PATIENT");
        // user_roles 关联通过原生 SQL 或 EntityManager
        userRepo.save(user);
        assignRole(user.getId(), "PATIENT");

        return Map.of("id", user.getId(), "username", user.getUsername());
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtProvider.validate(refreshToken)) throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效");
        var claims = jwtProvider.parse(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());
        String hash = jwtProvider.hashToken(refreshToken);
        RefreshToken rt = refreshRepo.findByTokenHash(hash);
        if (rt == null || rt.getRevoked()) throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌已失效");

        rt.setRevoked(true);
        refreshRepo.save(rt);

        User user = userRepo.findById(userId).orElseThrow();
        List<String> roles = getRoles(userId);
        String access = jwtProvider.generateAccessToken(userId, user.getUsername(), roles);
        String newRefresh = jwtProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, newRefresh);

        return Map.of("accessToken", access, "refreshToken", newRefresh, "expiresIn", 7200);
    }

    @Transactional
    public void logout(String refreshToken) {
        String hash = jwtProvider.hashToken(refreshToken);
        RefreshToken rt = refreshRepo.findByTokenHash(hash);
        if (rt != null) { rt.setRevoked(true); refreshRepo.save(rt); }
    }

    public Map<String, Object> me() {
        Long uid = SecurityUtils.getCurrentUserId();
        User user = userRepo.findById(uid).orElseThrow();
        List<String> roles = getRoles(uid);
        return buildUserInfo(user, roles);
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        Long uid = SecurityUtils.getCurrentUserId();
        User user = userRepo.findById(uid).orElseThrow();
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash()))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "原密码错误");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    // ---- helpers ----
    private void saveRefreshToken(Long userId, String token) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(jwtProvider.hashToken(token));
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProvider.getRefreshExpiration()));
        refreshRepo.save(rt);
    }

    @SuppressWarnings("unchecked")
    private List<String> getRoles(Long userId) {
        return userRoleService.getRoleCodes(userId);
    }

    private void assignRole(Long userId, String roleCode) {
        userRoleService.assignRole(userId, roleCode);
    }

    private Map<String, Object> buildUserInfo(User user, List<String> roles) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("roles", roles);
        info.put("mustChangePassword", user.getMustChangePassword());
        return info;
    }
}
