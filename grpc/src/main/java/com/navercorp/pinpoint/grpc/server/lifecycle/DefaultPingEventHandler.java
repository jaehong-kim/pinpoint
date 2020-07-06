/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.grpc.server.lifecycle;

import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.grpc.Header;
import com.navercorp.pinpoint.grpc.server.ServerContext;
import com.navercorp.pinpoint.grpc.server.TransportMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Woonduk Kang(emeroad)
 * @author jaehong.kim
 */
public class DefaultPingEventHandler implements PingEventHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final PingSessionRegistry pingSessionRegistry;
    private final LifecycleListener lifecycleListener;

    public DefaultPingEventHandler(PingSessionRegistry pingSessionRegistry, LifecycleListener lifecycleListener) {
        this.pingSessionRegistry = Assert.requireNonNull(pingSessionRegistry, "pingSessionRegistry");
        this.lifecycleListener = Assert.requireNonNull(lifecycleListener, "lifecycleListener");
    }

    @Override
    public void connect() {
        final TransportMetadata transportMetadata = ServerContext.getTransportMetadata();
        if (transportMetadata == null) {
            logger.info("Skip connect event handle of ping, not found TransportMetadata. header={}", ServerContext.getAgentInfo());
            return;
        }

        final Long transportId = transportMetadata.getTransportId();
        final Header header = ServerContext.getAgentInfo();
        final PingSession pingSession = new PingSession(transportId, header);
        final PingSession oldSession = pingSessionRegistry.add(pingSession.getId(), pingSession);
        if (oldSession != null) {
            logger.warn("Duplicated ping session old={}, new={}", oldSession, pingSession);
//                    cleanup old session
//                    oldSession.forceClose();
        }
        lifecycleListener.connect(pingSession);
    }

    @Override
    public void ping() {
//        lifecycleListener.handshake();
    }

    @Override
    public void close() {
        final TransportMetadata transportMetadata = ServerContext.getTransportMetadata();
        if (transportMetadata == null) {
            logger.info("Skip close event handle of ping, not found TransportMetadata. header={}", ServerContext.getAgentInfo());
            return;
        }

        final PingSession removedSession = pingSessionRegistry.remove(transportMetadata.getTransportId());
        if (removedSession == null) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Remove ping session. pingSession={}", removedSession);
        }
        lifecycleListener.close(removedSession);
    }

    @Override
    public void update(final short serviceType) {
        final TransportMetadata transportMetadata = ServerContext.getTransportMetadata();
        if (transportMetadata == null) {
            logger.info("Skip update event handle of ping, not found TransportMetadata. header={}", ServerContext.getAgentInfo());
            return;
        }

        final PingSession pingSession = pingSessionRegistry.get(transportMetadata.getTransportId());
        if (pingSession == null) {
            logger.info("Skip update event handle of ping, not found ping session. transportMetadata={}", transportMetadata);
            return;
        }
        pingSession.setServiceType(serviceType);
        if (!pingSession.isUpdated()) {
            lifecycleListener.connect(pingSession);
            pingSession.setUpdated(true);
        }
    }
}