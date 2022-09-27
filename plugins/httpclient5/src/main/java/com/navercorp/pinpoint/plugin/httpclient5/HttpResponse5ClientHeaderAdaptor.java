/*
 * Copyright 2021 NAVER Corp.
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

package com.navercorp.pinpoint.plugin.httpclient5;

import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.response.ResponseAdaptor;
import com.navercorp.pinpoint.common.util.ArrayUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class HttpResponse5ClientHeaderAdaptor implements ResponseAdaptor<ClassicHttpResponse> {

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    @Override
    public boolean containsHeader(ClassicHttpResponse response, String name) {
        return response.containsHeader(name);
    }

    @Override
    public void setHeader(ClassicHttpResponse response, String name, String value) {
        if (isDebug) {
            logger.debug("Set header {}={}", name, value);
        }
        response.setHeader(name, value);
    }

    @Override
    public void addHeader(ClassicHttpResponse response, String name, String value) {
        if (isDebug) {
            logger.debug("Add header {}={}", name, value);
        }
        response.addHeader(name, value);
    }

    @Override
    public String getHeader(ClassicHttpResponse response, String name) {
        final Header header = response.getFirstHeader(name);
        return header != null ? header.getValue() : null;
    }

    @Override
    public Collection<String> getHeaders(ClassicHttpResponse response, String name) {
        final Header[] headers = response.getHeaders(name);
        if (ArrayUtils.isEmpty(headers)) {
            return Collections.emptyList();
        }
        if (headers.length == 1) {
            return Collections.singletonList(headers[0].getValue());
        }
        Set<String> values = new HashSet<>(headers.length);
        for (Header header : headers) {
            values.add(header.getValue());
        }
        return values;
    }

    @Override
    public Collection<String> getHeaderNames(ClassicHttpResponse response) {
        final Header[] headers = response.getHeaders();
        if (ArrayUtils.isEmpty(headers)) {
            return Collections.emptyList();
        }
        if (headers.length == 1) {
            return Collections.singletonList(headers[0].getName());
        }
        Set<String> values = new HashSet<>(headers.length);
        for (Header header : headers) {
            values.add(header.getName());
        }
        return values;
    }

}
