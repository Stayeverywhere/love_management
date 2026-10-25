package com.lovemanagement.domain;

public enum MemberStatus {
    ACTIVE("在职"),
    INACTIVE("停用");

    private final String label;

    MemberStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
