package com.fsck.k9.mail.store.imap;


import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.fsck.k9.mail.store.imap.ImapResponseParser.equalsIgnoreCase;


class EnabledResponse {
    private final Set<String> capabilities;


    private EnabledResponse(Set<String> capabilities) {
        this.capabilities = Collections.unmodifiableSet(capabilities);
    }

    public static EnabledResponse parse(List<ImapResponse> responses) {
        EnabledResponse result = null;
        for (ImapResponse response : responses)
            if (result == null && response.getTag() == null)
                result = parse(response);
        return result;
    }

    static EnabledResponse parse(ImapList capabilityList) {
        if (capabilityList.isEmpty() || !equalsIgnoreCase(capabilityList.get(0), Responses.ENABLED)) {
            return null;
        }

        int size = capabilityList.size();
        HashSet<String> capabilities = new HashSet<>(size - 1);

        for (int i = 1; i < size; i++) {
            if (!capabilityList.isString(i)) {
                return null;
            }

            String uppercaseCapability = capabilityList.getString(i).toUpperCase(Locale.US);
            capabilities.add(uppercaseCapability);
        }

        return new EnabledResponse(capabilities);
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }
}
