package com.ntsocial.meshlink.core.gateway;

import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData;

/** Callback stream for validated NTsocial Gateway envelopes. */
oneway interface INtsocialEnvelopeCallback {
    void onNtsocialEnvelope(in NtsocialEnvelopeData envelope);
}
