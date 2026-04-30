package de.skerkewitz.jcme.model;

import java.util.List;

public record Organization(String baseUrl, List<Space> spaces) {
    public Organization {
        spaces = spaces == null ? List.of() : List.copyOf(spaces);
    }
}
