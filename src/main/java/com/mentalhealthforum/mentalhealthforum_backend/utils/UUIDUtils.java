package com.mentalhealthforum.mentalhealthforum_backend.utils;

import java.util.UUID;
import java.util.regex.Pattern;

public class UUIDUtils {

    private UUIDUtils(){}

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX);

    public static boolean isUUID(String str){
        return UUID_PATTERN.matcher(str).matches();
    }
}
