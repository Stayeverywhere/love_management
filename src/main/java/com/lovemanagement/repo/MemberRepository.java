package com.lovemanagement.repo;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByIdAsc();

    Page<Member> findAllByOrderByIdAsc(Pageable pageable);

    Page<Member> findByNameContainingOrderByIdAsc(String name, Pageable pageable);

    List<Member> findByStatusOrderByNameAsc(MemberStatus status);

    long countByStatus(MemberStatus status);
}
