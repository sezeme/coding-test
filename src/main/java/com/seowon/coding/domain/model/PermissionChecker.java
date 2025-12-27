package com.seowon.coding.domain.model;


import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

class PermissionChecker {

    /**
     * TODO #7: 코드를 최적화하세요
     * 테스트 코드`PermissionCheckerTest`를 활용하시면 리펙토링에 도움이 됩니다.
     */
    public static boolean hasPermission(
            String userId,
            String targetResource,
            String targetAction,
            List<User> users,
            List<UserGroup> groups,
            List<Policy> policies
    ) {
        // 1. 해당 권한 및 정책을 가지고 있는 policy 필터링
        // * 해당 policy를 가지고 있는 group 찾기
        List<Policy> foundPolicies = new ArrayList<>();
        for (Policy policy : policies) {
            for (Statement statement : policy.statements) {
                if (statement.actions.contains(targetAction) &&
                        statement.resources.contains(targetResource)) {
                    foundPolicies.add(policy);
                }
            }
        }
        if (foundPolicies.isEmpty()) return false;

        List<UserGroup> foundGroup = new ArrayList<>();
        for (Policy policy : foundPolicies) {
            for (UserGroup group : groups) {
                if (group.policyIds.contains(policy.id)) foundGroup.add(group);
            }
        }
        if (foundGroup.isEmpty()) return false;

        // 2. target user이 있는지 찾기
        User foundUser = null;
        for (User user : users) {
            if (user.id.equals(userId)) foundUser = user;
        }
        if (foundUser == null) return false;

        // 3. user가 해당 group에 소속되어있는지 확인
        for (UserGroup group : foundGroup) {
            if (foundUser.groupIds.contains(group.id)) return true;
        }
        return false;
    }
}

class User {
    String id;
    List<String> groupIds;

    public User(String id, List<String> groupIds) {
        this.id = id;
        this.groupIds = groupIds;
    }
}

class UserGroup {
    String id;
    List<String> policyIds;

    public UserGroup(String id, List<String> policyIds) {
        this.id = id;
        this.policyIds = policyIds;
    }
}

class Policy {
    String id;
    List<Statement> statements;

    public Policy(String id, List<Statement> statements) {
        this.id = id;
        this.statements = statements;
    }
}

class Statement {
    List<String> actions;
    List<String> resources;

    @Builder
    public Statement(List<String> actions, List<String> resources) {
        this.actions = actions;
        this.resources = resources;
    }
}