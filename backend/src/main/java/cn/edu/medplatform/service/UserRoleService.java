package cn.edu.medplatform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** 用户-角色关联查询（纯关联表，无独立实体） */
@Service
@RequiredArgsConstructor
public class UserRoleService {
    private final JdbcTemplate jdbc;

    public List<String> getRoleCodes(Long userId) {
        return jdbc.queryForList(
            "SELECT r.code FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ?",
            String.class, userId);
    }

    public List<String> getPermissionCodes(Long userId) {
        return jdbc.queryForList(
            "SELECT DISTINCT p.code FROM user_roles ur " +
            "JOIN role_permissions rp ON ur.role_id = rp.role_id " +
            "JOIN permissions p ON rp.permission_id = p.id WHERE ur.user_id = ?",
            String.class, userId);
    }

    public void assignRole(Long userId, String roleCode) {
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING", userId, roleId);
    }

    public void replaceRoles(Long userId, List<String> roleCodes) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (String code : roleCodes) {
            assignRole(userId, code);
        }
    }
}
