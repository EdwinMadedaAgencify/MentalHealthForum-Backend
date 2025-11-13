package com.mentalhealthforum.mentalhealthforum_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private String createResponseMessage(String accessLevel, Authentication authentication){
        String username = authentication.getName();
        String roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        return String.format(
        """
        %s
        Accessed by: %s
        Authorities: %s
        """, accessLevel, username, roles
        ).trim();
    }

    @PreAuthorize("hasRole('FORUM_MEMBER')")
    @GetMapping("/member")
    public ResponseEntity<String> memberResource(Authentication authentication){
        String message = createResponseMessage("Accessible to forum members.", authentication);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasRole('MODERATOR')")
    @GetMapping("/moderator")
    public ResponseEntity<String> moderatorResource(Authentication authentication){
        String message = createResponseMessage("Accessible to forum moderators.", authentication);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<String> adminResource(Authentication authentication){
        String message = createResponseMessage("Accessible to forum Admins.", authentication);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasAnyRole('FORUM_MEMBER', 'TRUSTED_MEMBER', 'PEER_SUPPORTER')")
    @GetMapping("/forum/topics")
    public ResponseEntity<String> forumTopics(Authentication authentication){
        String message = createResponseMessage("Accessible to all members (forum, trusted, peer supporter).", authentication);
        return ResponseEntity.ok(message);
    }

}
