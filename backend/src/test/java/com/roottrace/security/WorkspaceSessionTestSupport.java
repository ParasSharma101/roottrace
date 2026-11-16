package com.roottrace.security;

import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.support.IntegrationTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Transactional
abstract class WorkspaceSessionTestSupport extends IntegrationTestSupport {
    @Autowired protected UserRepository users;
    @Autowired protected WorkspaceRepository workspaces;
    @Autowired protected WorkspaceMemberRepository memberships;
    @Autowired protected JwtTokenProvider tokens;

    protected Fixture fixture(String role) {
        String unique = UUID.randomUUID().toString();
        User user = users.save(new User(unique + "@example.com", "not-used-for-password-login", "Test User"));
        Workspace workspace = workspaces.save(new Workspace("Test Workspace", unique, "fixture-key-" + unique));
        WorkspaceMember member = memberships.save(new WorkspaceMember(workspace, user, role));
        return new Fixture(user, workspace, member);
    }

    protected record Fixture(User user, Workspace workspace, WorkspaceMember member) {}
}
