package com.gabojago.security.oauth2;

import com.gabojago.user.enums.OAuthProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuthProviderClientRegistry {

    private final Map<OAuthProvider, OAuthProviderClient> clients;

    public OAuthProviderClientRegistry(List<OAuthProviderClient> providerClients) {
        this.clients = new EnumMap<>(OAuthProvider.class);
        providerClients.forEach(client -> clients.put(client.getProvider(), client));
    }

    public OAuthProviderClient get(OAuthProvider provider) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 OAuth provider: " + provider);
        }
        return client;
    }
}
