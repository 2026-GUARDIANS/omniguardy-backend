package com.omniguardy.backend.domain.visionsession.infrastructure.video;

import com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component @RequiredArgsConstructor
public class StreamingVideoLinkListener {
    private final LinkStreamingVideoInputPort links;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void eventLinked(StreamingVideoLinkRequested event) { links.afterEvent(event.sessionId()); }
}
