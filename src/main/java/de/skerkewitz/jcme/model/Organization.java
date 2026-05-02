package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;

import java.util.List;

public record Organization(BaseUrl baseUrl, List<Space> spaces) {
    public Organization {
        spaces = spaces == null ? List.of() : List.copyOf(spaces);
    }
}
