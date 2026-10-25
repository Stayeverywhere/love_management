package com.lovemanagement.service;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class MemberService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    private final MemberRepository memberRepository;
    private final ProjectTaskRepository taskRepository;
    private final UserAccountRepository userAccountRepository;

    public MemberService(MemberRepository memberRepository,
                         ProjectTaskRepository taskRepository,
                         UserAccountRepository userAccountRepository) {
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public List<Member> list() {
        return memberRepository.findAllByOrderByIdAsc();
    }

    public Page<Member> page(Pageable pageable) {
        return memberRepository.findAllByOrderByIdAsc(pageable);
    }

    public Page<Member> pageByName(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return memberRepository.findAllByOrderByIdAsc(pageable);
        }
        return memberRepository.findByNameContainingOrderByIdAsc(keyword.trim(), pageable);
    }

    public List<Member> assignable() {
        return memberRepository.findByStatusOrderByNameAsc(MemberStatus.ACTIVE);
    }

    public Member get(Long id) {
        return memberRepository.findById(id).orElseThrow(() -> new PolicyException("成员不存在"));
    }

    @Transactional
    public Member save(Long id, String name, String email, String phone, String position, MemberStatus status) {
        String normalizedName = checkName(name);
        String normalizedEmail = checkEmail(email);
        String normalizedPhone = checkPhone(phone);
        String normalizedPosition = checkPosition(position);
        if (status == null) {
            throw new PolicyException("请选择成员状态");
        }
        Member member = id == null ? new Member() : get(id);
        member.setName(normalizedName);
        member.setEmail(normalizedEmail);
        member.setPhone(normalizedPhone);
        member.setPosition(normalizedPosition);
        member.setStatus(status);
        return memberRepository.save(member);
    }

    @Transactional
    public void delete(Long id) {
        Member member = get(id);
        if (taskRepository.existsByAssignee_Id(id)) {
            throw new PolicyException("该成员仍负责任务，请先调整任务分配后再移除");
        }
        if (userAccountRepository.existsByMember_Id(id)) {
            throw new PolicyException("该成员已绑定登录账号，不能移除");
        }
        memberRepository.delete(member);
    }

    private String checkName(String name) {
        if (name == null || name.isBlank()) {
            throw new PolicyException("成员姓名不能为空");
        }
        String value = name.trim();
        if (value.length() < 2 || value.length() > 20) {
            throw new PolicyException("成员姓名长度须为 2 到 20 个字符");
        }
        return value;
    }

    private String checkEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String value = email.trim();
        if (!EMAIL.matcher(value).matches()) {
            throw new PolicyException("邮箱格式不正确");
        }
        return value;
    }

    private String checkPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String value = phone.trim();
        if (!PHONE.matcher(value).matches()) {
            throw new PolicyException("手机号须为 11 位，并以 1 开头");
        }
        return value;
    }

    private String checkPosition(String position) {
        if (position == null || position.isBlank()) {
            return "";
        }
        String value = position.trim();
        if (value.length() > 20) {
            throw new PolicyException("岗位不能超过 20 个字符");
        }
        return value;
    }
}
