package com.roottrace.security;

import com.roottrace.domain.model.User;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.UserRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.domain.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "roottrace.demo.enabled", havingValue = "true")
public class AuthDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(AuthDataSeeder.class);
    public static final UUID DEFAULT_WORKSPACE_ID = Workspace.LEGACY_WORKSPACE_ID;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthDataSeeder(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedInitialData() {
        // Ensure default workspace
        Workspace defaultWorkspace = workspaceRepository.findById(DEFAULT_WORKSPACE_ID)
                .orElseGet(() -> {
                    Workspace ws = new Workspace("RootTrace Cloud Dev", "roottrace-dev", "roottrace_dev_secret_key");
                    ws.setId(DEFAULT_WORKSPACE_ID);
                    return workspaceRepository.save(ws);
                });

        seedUser("alice.sre@roottrace.io", "Alice (On-Call SRE)", "password123", UserRole.ROLE_SRE.name(), defaultWorkspace);
        seedUser("bob.admin@roottrace.io", "Bob (Platform Admin)", "password123", UserRole.ROLE_ADMIN.name(), defaultWorkspace);
        seedUser("charlie.dev@roottrace.io", "Charlie (Dev Observer)", "password123", UserRole.ROLE_VIEWER.name(), defaultWorkspace);

        log.info("Auth & Workspace baseline verification complete. Default workspace [{}] active.", defaultWorkspace.getSlug());
    }

    private void seedUser(String email, String fullName, String rawPassword, String role, Workspace workspace) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User(email, passwordEncoder.encode(rawPassword), fullName);
            user = userRepository.save(user);

            WorkspaceMember member = new WorkspaceMember(workspace, user, role);
            workspaceMemberRepository.save(member);

            log.info("Seeded demo user [{}] with role [{}]", email, role);
        }
    }
}
