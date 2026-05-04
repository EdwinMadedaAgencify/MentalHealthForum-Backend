package com.mentalhealthforum.mentalhealthforum_backend.utils;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.function.Function;

@Component
public class SlugsUtil {
    /**
     * Generates a URL-friendly slug from a name
     * Examples:
     * "Crisis Support" -> "crisis-support"
     * "General Chat!" -> "general-chat"
     * "FAQ & Help" -> "faq-help"
     */
    public static String generateSlug(String name){
        if(name == null || name.isBlank()){
            return "";
        }

        return name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
                .replaceAll("\\s+","-") // Replace spaces with hyphens
                .replaceAll("-+","-") // Replace multiple hyphens with single
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

    /**
     * Generates a unique slug by appending a suffix if needed
     * */
    public static String generateUniqueSlug(String name, Function<String, Boolean> existsChecker){
        String baseSlug = generateSlug(name);
        String slug = baseSlug;
        int counter = 1;

        while (existsChecker.apply(slug)){
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    // Add to SlugUtils
    public static Mono<String> generateUniqueSlugReactive(String name, Function<String, Mono<Boolean>> slugExistsChecker) {
        String baseSlug = generateSlug(name);
        return checkUniquenessReactive(baseSlug, 1, slugExistsChecker);
    }

    private static Mono<String> checkUniquenessReactive(String baseSlug, int counter,
                                                        Function<String, Mono<Boolean>> slugExistsChecker) {
        String slug = counter == 1 ? baseSlug : baseSlug + "-" + (counter - 1);

        return slugExistsChecker.apply(slug)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(slug);
                    }
                    return checkUniquenessReactive(baseSlug, counter + 1, slugExistsChecker);
                });
    }
}
