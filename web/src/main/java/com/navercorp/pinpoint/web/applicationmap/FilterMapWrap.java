/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.applicationmap;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.navercorp.pinpoint.web.applicationmap.histogram.LoadHistogramFormat;
import com.navercorp.pinpoint.web.applicationmap.link.Link;
import com.navercorp.pinpoint.web.applicationmap.nodes.Node;
import com.navercorp.pinpoint.web.view.FilterMapWrapSerializer;

/**
 * @author emeroad
 * @author jaehong.kim
 */
@JsonSerialize(using = FilterMapWrapSerializer.class)
public class FilterMapWrap {
    private final ApplicationMap applicationMap;
    private Long lastFetchedTimestamp;

    public FilterMapWrap(ApplicationMap applicationMap) {
        this(applicationMap, LoadHistogramFormat.V1);
    }

    public FilterMapWrap(ApplicationMap applicationMap, LoadHistogramFormat loadHistogramFormat) {
        this.applicationMap = applicationMap;
        if (loadHistogramFormat == LoadHistogramFormat.V2) {
            for (Node node : this.applicationMap.getNodes()) {
                node.setLoadHistogramFormat(loadHistogramFormat);
            }
            for (Link link : this.applicationMap.getLinks()) {
                link.setLoadHistogramFormat(loadHistogramFormat);
            }
        }
    }

    public void setLastFetchedTimestamp(Long lastFetchedTimestamp) {
        this.lastFetchedTimestamp = lastFetchedTimestamp;
    }

    public ApplicationMap getApplicationMap() {
        return applicationMap;
    }

    public Long getLastFetchedTimestamp() {
        return lastFetchedTimestamp;
    }
}
