package com.lovemanagement.service;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.repo.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 成员管理扩展：启用/禁用、批量导入、操作日志。 */
@Service
public class MemberAdminService {

    private final MemberRepository memberRepository;
    private final OperationLogService logService;

    public MemberAdminService(MemberRepository memberRepository, OperationLogService logService) {
        this.memberRepository = memberRepository;
        this.logService = logService;
    }

    @Transactional
    public Member setEnabled(Long id, boolean enabled) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new PolicyException("成员不存在"));
        member.setStatus(enabled ? MemberStatus.ACTIVE : MemberStatus.INACTIVE);
        memberRepository.save(member);
        logService.log(enabled ? "成员启用" : "成员禁用", "MEMBER", id,
                (enabled ? "启用" : "禁用") + "成员「" + member.getName() + "」");
        return member;
    }

    @Transactional
    public List<Member> bulkImport(List<String> names, List<String> emails) {
        List<Member> created = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i) == null ? "" : names.get(i).trim();
            String email = i < emails.size() && emails.get(i) != null ? emails.get(i).trim() : "";
            if (name.isEmpty()) continue;
            if (memberRepository.findByName(name).isPresent()) continue;
            Member m = new Member();
            m.setName(name);
            m.setEmail(email.isEmpty() ? null : email);
            m.setStatus(MemberStatus.ACTIVE);
            created.add(memberRepository.save(m));
        }
        logService.log("成员批量导入", "MEMBER", null,
                "导入 " + created.size() + " 名成员");
        return created;
    }
}
