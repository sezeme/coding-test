package com.seowon.coding.domain.model;


import com.seowon.coding.util.ListFun;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
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
        // 1. target user 찾기
        HashMap<String, User> userMap = ListFun.toHashMap(users, User::getId);
        User foundUser = userMap.get(userId);
        if (foundUser == null) return false;

        // 2. 해당 권한 및 정책을 가지고 있는 policy 필터링
        List<String> foundPolicyIds = new ArrayList<>();
        for (Policy policy : policies) {
            for (Statement statement : policy.statements) {
                if (statement.actions.contains(targetAction) &&
                        statement.resources.contains(targetResource)) {
                    foundPolicyIds.add(policy.id);
                    break;
                }
            }
        }
        if (foundPolicyIds.isEmpty()) return false;

        // 3. groupId -> UserGroup 맵
        HashMap<String, UserGroup> groupMap =
                ListFun.toHashMap(groups, UserGroup::getId);

        // 4. 사용자가 속한 group 중 하나라도 정책을 만족하면 true
        for (String groupId : foundUser.groupIds) {
            UserGroup group = groupMap.get(groupId);
            if (group == null) continue;

            for (String policyId : group.policyIds) {
                if (foundPolicyIds.contains(policyId)) return true;
            }
        }

        return false;
    }
}

class User {
    @Getter
    String id;
    List<String> groupIds;

    public User(String id, List<String> groupIds) {
        this.id = id;
        this.groupIds = groupIds;
    }
}

class UserGroup {
    @Getter
    String id;
    List<String> policyIds;

    public UserGroup(String id, List<String> policyIds) {
        this.id = id;
        this.policyIds = policyIds;
    }
}

class Policy {
    @Getter
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