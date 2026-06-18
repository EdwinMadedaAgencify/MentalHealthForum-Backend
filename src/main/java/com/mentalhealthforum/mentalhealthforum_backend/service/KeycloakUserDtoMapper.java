package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.user.KeycloakUserDto;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting Keycloak's {@link UserRepresentation} to the application's
 * {@link KeycloakUserDto}.
 *
 * <p>This mapper is used across the service layer to convert Keycloak user data
 * into a consistent internal DTO format.</p>
 */
@Component
public class KeycloakUserDtoMapper {

    /**
     * Converts a Keycloak UserRepresentation to a KeycloakUserDto.
     *
     * @param userRep The Keycloak user representation (from admin client)
     * @return A KeycloakUserDto containing the essential user data
     */
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
