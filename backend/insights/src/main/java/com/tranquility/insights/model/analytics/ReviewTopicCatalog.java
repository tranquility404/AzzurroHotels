package com.tranquility.insights.model.analytics;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReviewTopicCatalog {

    public static final String SUGGESTION_PATTERN = "\\m(should|could|would|wish|suggest(ed|ion)?|recommend(ed|ation)?|please|needs? to|would be nice|please consider)\\M";

    public static final List<Topic> TOPICS = List.of(
            new Topic("Cleanliness", "\\m(cleanliness|clean(ing|ed|er)?|dirty|dirt|dust|stain(ed|s)?|smell|odor|odour|mold|mould|hygiene|housekeeping|filthy|spotless|limpieza|sucio|sucia|polvo|sauberkeit|schmutzig|staub|propreté|sale|poussière)\\M"),
            new Topic("Check-in experience", "\\m(check[ -]?in|check[ -]?out|arrival|arrived|queue|registration|einchecken|anreise|enregistrement|registro|entrada)\\M"),
            new Topic("Staff / receptionist behaviour", "\\m(staff|reception(ist|ists)?|front desk|employee(s)?|rude|friendly|helpful|unhelpful|polite|attentive|professional|manager|personnel|réception|personal|recepción|mitarbeiter|rezeption)\\M"),
            new Topic("Noise", "\\m(noise|noisy|loud|quiet|sound(s)?|neighbor(s)?|neighbour(s)?|traffic|construction|music|party|thin wall(s)?|ruido|bruit|lärm|laut)\\M"),
            new Topic("Facilities", "\\m(facilit(y|ies)|amenit(y|ies)|pool|gym|spa|parking|elevator|lift|wifi|wi-fi|internet|laundry|kitchen|sauna|instalaciones|servicios|ausstattung|einrichtung)\\M"),
            new Topic("Location", "\\m(location|located|walk(able|ing)?|distance|near(by)?|close|far|transport|station|airport|beach|center|centre|area|neighborhood|neighbourhood|accessible|convenient|ubicación|emplacement|lage|standort)\\M"),
            new Topic("Room condition", "\\m(room(s)?|bed(s)?|mattress|bathroom(s)?|shower(s)?|toilet(s)?|furniture|decor|spacious|cramped|comfortable|comfort|balcony|view|window(s)?|door(s)?|habitación|cama|baño|chambre|lit|zimmer|bett)\\M"),
            new Topic("Value for money", "\\m(value|price(s)?|cost(s)?|expensive|cheap|overpriced|worth|money|rate(s)?|fee(s)?|charge(d|s)?|affordable|budget|refund|precio|caro|barato|prix|cher|preis|teuer|günstig)\\M"),
            new Topic("Food and dining", "\\m(food|breakfast|dinner|lunch|meal(s)?|restaurant|menu|buffet|coffee|tea|dining|chef|cuisine|comida|desayuno|cena|repas|frühstück|essen)\\M")
    );

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cleaning", "Cleanliness"), Map.entry("clean", "Cleanliness"),
            Map.entry("checkin", "Check-in experience"), Map.entry("check in", "Check-in experience"),
            Map.entry("staff", "Staff / receptionist behaviour"), Map.entry("reception", "Staff / receptionist behaviour"),
            Map.entry("room", "Room condition"), Map.entry("rooms", "Room condition"),
            Map.entry("price", "Value for money"), Map.entry("value", "Value for money"),
            Map.entry("food", "Food and dining"), Map.entry("dining", "Food and dining")
    );

    private ReviewTopicCatalog() {
    }

    static Topic resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String normalized = requested.trim().toLowerCase(Locale.ROOT);
        String canonical = ALIASES.getOrDefault(normalized, requested.trim());
        return TOPICS.stream()
                .filter(topic -> topic.name().equalsIgnoreCase(canonical))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown topic. Available topics: "
                        + TOPICS.stream().map(Topic::name).reduce((a, b) -> a + ", " + b).orElse("none") + "."));
    }

    public record Topic(String name, String regex) {
    }
}
