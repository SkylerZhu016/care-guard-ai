package cn.edu.medplatform.service;

import cn.edu.medplatform.entity.AuditLog;
import cn.edu.medplatform.repository.AuditLogRepository;
import cn.edu.medplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String username, String role, String action, String objectType, String objectId, String before, String after) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setRole(role);
        log.setAction(action);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setBeforeSummary(before != null && before.length() > 1000 ? before.substring(0, 1000) : before);
        log.setAfterSummary(after != null && after.length() > 1000 ? after.substring(0, 1000) : after);
        log.setCreatedAt(LocalDateTime.now());
        // IP + traceId
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null) {
                HttpServletRequest req = attr.getRequest();
                log.setIp(req.getRemoteAddr());
                log.setTraceId((String) req.getAttribute("traceId"));
            }
        } catch (Exception ignored) { }
        repo.save(log);
    }

    public void logCurrent(String action, String objectType, String objectId, String after) {
        log(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername(),
            SecurityUtils.getCurrentRoles().stream().findFirst().orElse(""),
            action, objectType, objectId, null, after);
    }
}
