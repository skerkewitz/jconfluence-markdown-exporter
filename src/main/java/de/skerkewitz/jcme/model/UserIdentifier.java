package de.skerkewitz.jcme.model;

/**
 * Discriminated kind of user identifier accepted by Confluence's user-lookup endpoints.
 *
 * <p>Confluence Cloud uses {@link AccountId}; older Server instances expose either a
 * legacy {@link Username} or an opaque {@link UserKey}. Wrapping each in its own type
 * prevents accidentally calling {@code getUserByAccountId} with a username string.
 */
public sealed interface UserIdentifier
        permits UserIdentifier.AccountId, UserIdentifier.Username, UserIdentifier.UserKey {

    String value();

    record AccountId(String value) implements UserIdentifier {
        public AccountId {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("AccountId must be non-empty");
            }
        }
    }

    record Username(String value) implements UserIdentifier {
        public Username {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Username must be non-empty");
            }
        }
    }

    record UserKey(String value) implements UserIdentifier {
        public UserKey {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("UserKey must be non-empty");
            }
        }
    }
}
