package com.lovemanagement.domain;

/** 数据权限范围：角色可见的数据粒度。 */
public enum DataScope {

    SELF("仅自己"),
    TEAM("本团队"),
    ALL("全部数据");

    private final String label;

    DataScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
