package com.saasno.utility;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.saasno.entity.SessionEntity;
import java.util.Set;

@Component
public class SessionUtility {

    public SessionEntity get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SessionEntity)) {
            return null;
        }
        return (SessionEntity) auth.getPrincipal();
    }

    public String getUserId() {
        SessionEntity session = get();
        return session != null ? session.getUserId() : null;
    }

    public String getUsername() {
        SessionEntity session = get();
        return session != null ? session.getUsername() : null;
    }

    public Set<String> getRoles() {
        SessionEntity session = get();
        return session != null ? session.getRoles() : null;
    }

}
