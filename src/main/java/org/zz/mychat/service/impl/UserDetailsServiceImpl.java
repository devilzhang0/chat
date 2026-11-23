package org.zz.mychat.service.impl;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.User;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if ("admin".equals(username)) {
            User user = new User();
            user.setId(1L);
            user.setName("admin");
            user.setPassword("$2a$10$zQrRin.WVhaK9791m2Q81emI5uUH6h6yszfJPMXncsLXfusBrk2PW");
            return user;
        }

        return null;
    }
}
