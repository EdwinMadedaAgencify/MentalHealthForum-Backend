package com.mentalhealthforum.mentalhealthforum_backend.utils;

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
}
