package com.iorigination.configportal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "country_master")
public class CountryMaster {
    @Id
    private String countryCode;   // DE, SE, UK
    private String countryName;
    private String currency;
    private String language;
    private String timezone;
    private String regulatoryBody;
    private String dataRegion;
    private String status;        // LIVE | PLANNED | SUNSET
    private List<String> activeProducts;
    private String launchDate;
    private String flagEmoji;
}
