package com.ntsocial.meshlink.core.gateway;

import com.ntsocial.meshlink.core.gateway.INtsocialEnvelopeCallback;
import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData;
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayStatus;

/**
 * Protected NTsocial Gateway IPC.
 *
 * This project-owned contract is the integration boundary for the NTsocial app.
 * It intentionally exposes only the NTsocial envelope data plane, not the legacy
 * Meshtastic IMeshService control surface.
 */
interface INtsocialGatewayService {
    int sendNtsocialPayload(in int channelIndex, in byte []payload);

    void observeNtsocialEnvelope(in INtsocialEnvelopeCallback callback);

    void stopObservingNtsocialEnvelope(in INtsocialEnvelopeCallback callback);

    NtsocialGatewayStatus getGatewayStatus();

    List<NtsocialEnvelopeData> getCachedNtsocialEnvelopes();
}
