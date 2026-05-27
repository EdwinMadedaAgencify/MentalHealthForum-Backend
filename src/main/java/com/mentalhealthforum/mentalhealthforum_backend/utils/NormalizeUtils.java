package com.mentalhealthforum.mentalhealthforum_backend.utils;

import java.util.List;
import java.util.stream.Collectors;

public class NormalizeUtils {
    private NormalizeUtils(){
        // Prevent instantiation
    }

    public static String normalizeUnicode(String text){
        if(text == null) return "";

        // Simple normalization - can be enhanced
        return text.replaceAll("[áàâäåãā]", "a")
                .replaceAll("[ÁÀÂÄÅÃĀ]", "A")
                .replaceAll("[éèêëēėę]", "e")
                .replaceAll("[ÉÈÊËĒĖĘ]", "E")
                .replaceAll("[íìîïīį]", "i")
                .replaceAll("[ÍÌÎÏĪĮ]", "I")
                .replaceAll("[óòôöõøō]", "o")
                .replaceAll("[ÓÒÔÖÕØŌ]", "O")
                .replaceAll("[úùûüū]", "u")
                .replaceAll("[ÚÙÛÜŪ]", "U")
                .replaceAll("[ýÿ]", "y")
                .replaceAll("[ÝŸ]", "Y")
                .replaceAll("ñ", "n")
                .replaceAll("Ñ", "N")
                .replaceAll("ç", "c")
                .replaceAll("Ç", "C")
                .replaceAll("ß", "ss");
    }

    public static List<String> normalizeTags(List<String> tags){
        if(tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = tags.stream()
                .map(NormalizeUtils::normalizeTag)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeTag(String tag){
        if(tag == null) return "";
        return normalizeUnicode(tag)
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
