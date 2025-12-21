package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.KeycloakUserDto;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

@Component
public class KeycloakUserDtoMapper {
    public KeycloakUserDto mapToKeycloakUserDto(UserRepresentation userRep){
        return new KeycloakUserDto(
                userRep.getId(),
                userRep.getUsername(),
                userRep.getFirstName(),
                userRep.getLastName(),
                userRep.getEmail(),
                userRep.isEnabled(),
                userRep.isEmailVerified(),
                userRep.getCreatedTimestamp()
        );
    }
}
