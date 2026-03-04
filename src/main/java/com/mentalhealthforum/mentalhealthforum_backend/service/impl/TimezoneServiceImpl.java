package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.timezone.TimezoneDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.timezone.TimezonesResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.TimezoneService;
import com.mentalhealthforum.mentalhealthforum_backend.validation.timezone.TimezoneValidator;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimezoneServiceImpl implements TimezoneService {
    @Override
    public TimezonesResponse getTimezonesGrouped() {
        Map<String, List<TimezoneDetails>> grouped = ZoneId.getAvailableZoneIds().stream()
                .sorted()
                .map(this::createTimezoneResponse)
                .collect(Collectors.groupingBy(
                        TimezoneDetails::region,
                        TreeMap::new,
                        Collectors.toList()
                ));

        return new TimezonesResponse(grouped, null, countTotalZones());
    }

    @Override
    public TimezonesResponse getTimezonesFlat() {
        List<TimezoneDetails> flat = ZoneId.getAvailableZoneIds().stream()
                .sorted()
                .map(this::createTimezoneResponse)
                .sorted(Comparator.comparing(TimezoneDetails::displayName))
                .toList();

        return new TimezonesResponse(null, flat, flat.size());
    }

    @Override
    public TimezoneDetails getTimezoneDetails(String zoneId){
        if(zoneId == null || zoneId.isBlank()) return null;
        return createTimezoneResponse(zoneId);
    }

    private TimezoneDetails createTimezoneResponse(String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        ZonedDateTime now = ZonedDateTime.now(zone);

        String offset = now.getOffset().getId().replace("Z", "+00:00");

        String displayName = zone.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        if(displayName.equals(zoneId)){
            displayName = Arrays.stream(zoneId.split("/"))
                    .map(part -> part.replace("_", " "))
                    .collect(Collectors.joining(" - "));
        }

        String region = zoneId.contains("/")? zoneId.substring(0, zoneId.indexOf("/")): "Other";

        return new TimezoneDetails(zoneId, displayName, offset, region);
    }

    private long countTotalZones() {
        return ZoneId.getAvailableZoneIds().size();
    }

    @Override
    public String canonicalizeTimezone(String timezone) {
        return TimezoneValidator.canonicalize(timezone);
    }
}
