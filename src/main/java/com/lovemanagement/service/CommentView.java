package com.lovemanagement.service;

import com.lovemanagement.domain.TaskComment;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 评论展示节点：顶级评论 + 其回复列表。 */
@Getter
@Setter
public class CommentView {

    private TaskComment comment;
    private String authorName;
    private String time;
    private List<CommentView> replies = new ArrayList<>();
}
