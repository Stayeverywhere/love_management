package com.lovemanagement.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "member_function_permission",
       uniqueConstraints = @UniqueConstraint(name = "uk_mfp_member_perm", columnNames = {"member_id", "permission_id"}))
public class MemberFunctionPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    public MemberFunctionPermission() {
    }

    public MemberFunctionPermission(Long memberId, Long permissionId) {
        this.memberId = memberId;
        this.permissionId = permissionId;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
}
