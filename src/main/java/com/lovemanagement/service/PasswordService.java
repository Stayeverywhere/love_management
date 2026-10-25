package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(UserAccount account, String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new PolicyException("当前密码不正确");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new PolicyException("两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new PolicyException("新密码不能与当前密码相同");
        }
        PasswordPolicy.check(account.getUsername(), newPassword);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);
    }
}
