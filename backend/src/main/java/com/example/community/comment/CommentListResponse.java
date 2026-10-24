package com.example.community.comment;

import java.util.List;

/** 댓글 목록 응답 (CMT-02 ④). 객체로 감싸 두어, 나중에 페이지 정보를 더해도 응답 형식이 깨지지 않게 한다 (01 D-27). */
public record CommentListResponse(List<CommentResponse> items) {}
