package com.crimson.wifimonitor.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WlanEventAnalyzer {

    public List<String> inferCauses(String eventLogText) {
        if (eventLogText == null || eventLogText.isBlank()) {
            return List.of();
        }
        String text = eventLogText.toLowerCase(Locale.ROOT);
        List<String> causes = new ArrayList<>();
        if (containsAny(text, "authentication failed", "reason code: 2", "802.1x")) {
            causes.add("Possible authentication failure in WLAN AutoConfig events");
        }
        if (containsAny(text, "driver", "reset", "hardware")) {
            causes.add("Possible driver or adapter reset mentioned in WLAN AutoConfig events");
        }
        if (containsAny(text, "the network is disconnected", "disconnect", "association")) {
            causes.add("WLAN AutoConfig recorded a disconnect or association change");
        }
        if (containsAny(text, "failed to connect", "no matching network", "profile")) {
            causes.add("Possible network profile or configuration problem");
        }
        if (containsAny(text, "deauth", "disassoc", "the network ended the connection", "reason code: 3")) {
            causes.add("Possible router-initiated disconnect (deauth/disassoc language in events)");
        }
        return causes;
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
