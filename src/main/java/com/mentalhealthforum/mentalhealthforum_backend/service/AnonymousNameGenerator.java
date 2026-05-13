package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnonymousNameGenerator {

    private static final List<String> ANIMALS = List.of(
            // Birds (universally positive/majestic)
            "Eagle", "Hawk", "Falcon", "Owl", "Raven", "Crow", "Magpie", "Kestrel",
            "Osprey", "Swan", "Dove", "Robin", "Sparrow", "Finch", "Bluebird",
            "Cardinal", "Oriole", "Wren", "Chickadee", "Canary", "Parrot",
            "Cockatoo", "Macaw", "Lovebird", "Penguin", "Flamingo", "Pelican",
            "Albatross", "Heron", "Egret", "Crane", "Kingfisher", "Loon",

            // Big cats & wild cats (powerful, respected)
            "Lion", "Tiger", "Leopard", "Jaguar", "Cheetah", "Panther", "Cougar",
            "Lynx", "Bobcat", "SnowLeopard", "CloudedLeopard",

            // Canines (loyal, clever)
            "Wolf", "Fox", "Coyote", "Jackal", "Dingo", "ManedWolf", "Fennec",

            // Bears & forest mammals (strong, cute)
            "Bear", "PolarBear", "BrownBear", "BlackBear", "Panda", "Raccoon",
            "Badger", "Otter", "Beaver", "Marten", "Fisher",

            // Hoofed mammals (gentle, majestic)
            "Deer", "Elk", "Moose", "Caribou", "Bison", "Buffalo", "Antelope",
            "Gazelle", "Impala", "Springbok", "Ibex", "Ram", "Goat", "Zebra",
            "Giraffe", "Okapi", "Tapir", "Rhinoceros", "Hippopotamus",

            // Marine mammals (friendly, intelligent)
            "Dolphin", "Whale", "Orca", "Beluga", "Narwhal", "Seal", "SeaLion",
            "Walrus", "Manatee", "Dugong", "Capybara",

            // Primates (excluding "monkey/ape" due to slang)
            "Gorilla", "Chimpanzee", "Bonobo", "Orangutan", "Gibbon", "Lemur",
            "Marmoset", "Tamarin",

            // Marsupials (unique, charming)
            "Kangaroo", "Wallaby", "Koala", "Wombat", "TasmanianDevil", "Possum",
            "Bandicoot", "Quokka", "Cuscus",

            // Small cute mammals (safe, friendly)
            "Squirrel", "Rabbit", "Hare", "Chipmunk", "PrairieDog", "Groundhog",
            "Marmot", "Gopher", "Mole", "Hamster", "GuineaPig", "Chinchilla",
            "Hedgehog", "Porcupine", "Armadillo", "Pangolin", "Aardvark", "Sloth",

            // Reptiles (neutral, cool - avoid snake varieties)
            "Lizard", "Gecko", "Iguana", "Chameleon", "Skink", "Anole", "Turtle",
            "Tortoise", "Terrapin",

            // Amphibians (harmless, interesting)
            "Frog", "Toad", "TreeFrog", "Bullfrog", "Salamander", "Newt", "Axolotl",

            // Fish (neutral, oceanic)
            "Salmon", "Trout", "Bass", "Cod", "Tuna", "Marlin", "Swordfish", "Ray",
            "Eel", "Catfish", "Goldfish", "Koi", "Clownfish", "Angelfish",

            // Insects (beautiful/neutral ones only)
            "Butterfly", "Moth", "Dragonfly", "Firefly", "Ladybug", "Bee",
            "Grasshopper", "Cricket", "Beetle",

            // Crustaceans & molluscs (interesting sea creatures)
            "Crab", "Lobster", "Crayfish", "Octopus", "Squid", "Cuttlefish",
            "Jellyfish", "Starfish", "SeaUrchin",

            // Mythical (fun, no baggage)
            "Phoenix", "Dragon", "Griffin", "Pegasus", "Unicorn", "Fairy",
            "Elf", "Sprite", "Yeti", "Kraken"
    );

    public String generateAnonymousName(String userId, String threadId){
        int hash = (userId + threadId).hashCode();
        int index = Math.abs(hash) % ANIMALS.size();
        return "Anonymous_" + ANIMALS.get(index);
    }

    public String normalizeCustomAnonymousName(String customName){
        if(customName == null || customName.isBlank()){
            return "Anonymous_User";
        }

        String normalized = NormalizeUtils.normalizeUnicode(customName)
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("\\s+", "-");

        // Remove empty or too short
        if(normalized.isEmpty()){
            return "Anonymous_User";
        }

        // Limit length (max 30 characters after "Anonymous_"
        if(normalized.length() > 30){
            normalized = normalized.substring(0, 30);
        }
        return "Anonymous_" + normalized;
    }
}
