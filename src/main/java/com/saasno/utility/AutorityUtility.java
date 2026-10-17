package com.saasno.utility;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

public class AutorityUtility {

    public static Set<String> toRoleSet(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null)
            return Set.of();

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
