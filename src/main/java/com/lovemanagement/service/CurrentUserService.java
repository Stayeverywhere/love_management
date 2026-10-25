package com.lovemanagement.service;

import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public UserAccount currentOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userAccountRepository.findByUsername(authentication.getName()).orElse(null);
    }

    public UserAccount requireCurrent() {
        UserAccount account = currentOrNull();
        if (account == null) {
            throw new IllegalStateException("当前没有登录用户");
        }
        return account;
    }
}
