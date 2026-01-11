package com.mentalhealthforum.mentalhealthforum_backend.contants;

public class KeycloakAttributes {

    // Prevent instantiation
    private KeycloakAttributes(){}

    /**
     * Boolean flag: 'true' if the user has been successfully
     * persisted to the local application database.
     */
    public static final String IS_SYNCED_LOCALLY = "is_synced_locally";
    public static final String INVITED_BY = "invited_by";

    // Future-proofing: might or might not be added later
    // public static final String INVITED_BY = "invited_by";
}

