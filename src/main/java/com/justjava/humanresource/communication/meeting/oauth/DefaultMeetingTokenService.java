package com.justjava.humanresource.communication.meeting.oauth;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultMeetingTokenService implements MeetingTokenService {

    private final Map<MeetingProvider, MeetingTokenClient> clients = new EnumMap<>(MeetingProvider.class);
    private final Map<MeetingProvider, MeetingAccessToken> cache = new EnumMap<>(MeetingProvider.class);

    public DefaultMeetingTokenService(List<MeetingTokenClient> tokenClients) {
        tokenClients.forEach(client -> clients.put(client.provider(), client));
    }

    @Override
    public synchronized MeetingAccessToken accessToken(MeetingProvider provider) {
        MeetingTokenClient client = clients.get(provider);
        if (client == null) {
            throw new MeetingCredentialException("No token client is registered for " + provider);
        }
        MeetingAccessToken cached = cache.get(provider);
        Instant now = Instant.now();
        if (cached != null && !cached.expiresSoon(now)) {
            return cached;
        }
        MeetingAccessToken fetched = client.fetchToken();
        cache.put(provider, fetched);
        return fetched;
    }
}
