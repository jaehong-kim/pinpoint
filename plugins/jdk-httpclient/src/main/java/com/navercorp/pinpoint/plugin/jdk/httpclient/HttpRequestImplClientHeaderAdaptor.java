/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.jdk.httpclient;

import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.request.ClientHeaderAdaptor;
import jdk.internal.net.http.HttpRequestImpl;

/**
 * @author jaehong.kim
 */
public class HttpRequestImplClientHeaderAdaptor implements ClientHeaderAdaptor<HttpRequestImpl> {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    @Override
    public void setHeader(HttpRequestImpl httpRequestImpl, String name, String value) {
        httpRequestImpl.setSystemHeader(name, value);
        if (isDebug) {
            logger.debug("Set header {}={}", name, value);
        }
    }
}