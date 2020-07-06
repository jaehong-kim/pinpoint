/*
 * Copyright 2020 NAVER Corp.
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

package com.navercorp.pinpoint.web.mapper;

import com.navercorp.pinpoint.common.buffer.Buffer;
import com.navercorp.pinpoint.common.buffer.FixedBuffer;
import com.navercorp.pinpoint.common.hbase.RowMapper;
import com.navercorp.pinpoint.common.profiler.util.ApplicationMapStatisticsUtils;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.TimeUtils;
import com.navercorp.pinpoint.loader.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataMap;
import com.navercorp.pinpoint.web.service.ApplicationFactory;
import com.navercorp.pinpoint.web.vo.Application;
import com.sematext.hbase.wd.RowKeyDistributorByHashPrefix;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author netspider
 * @author jaehong.kim
 */
@Component
public class MapStatisticsCalleeCompactMapper implements RowMapper<LinkDataMap> {
    private final LinkFilter filter;
    @Autowired
    private ServiceTypeRegistryService registry;
    @Autowired
    private ApplicationFactory applicationFactory;
    @Autowired
    @Qualifier("statisticsCalleeCompactRowKeyDistributor")
    private RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix;

    public MapStatisticsCalleeCompactMapper() {
        this(SkipLinkFilter.FILTER);
    }

    public MapStatisticsCalleeCompactMapper(LinkFilter filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    @Override
    public LinkDataMap mapRow(Result result, int rowNum) throws Exception {
        if (result.isEmpty()) {
            return new LinkDataMap();
        }

        final byte[] rowKey = getOriginalKey(result.getRow());
        final Buffer row = new FixedBuffer(rowKey);
        final Application calleeApplication = readCalleeApplication(row);
        final long timestamp = TimeUtils.recoveryTimeMillis(row.readLong());
        final LinkDataMap linkDataMap = new LinkDataMap();
        for (Cell cell : result.rawCells()) {
            final byte[] qualifier = CellUtil.cloneQualifier(cell);
            final Application callerApplication = readCallerApplication(qualifier, calleeApplication.getServiceType());
            if (filter.filter(callerApplication)) {
                continue;
            }

            long requestCount = Bytes.toLong(cell.getValueArray(), cell.getValueOffset());
            short histogramSlot = ApplicationMapStatisticsUtils.getHistogramSlotFromColumnName(qualifier);
            boolean isError = histogramSlot == (short) -1;
            final short slotTime = (isError) ? (short) -1 : histogramSlot;
            linkDataMap.addLinkData(callerApplication, calleeApplication, calleeApplication.getName(), timestamp, slotTime, requestCount);
        }

        return linkDataMap;
    }

    private Application readCallerApplication(byte[] qualifier, ServiceType calleeServiceType) {
        short callerServiceType = ApplicationMapStatisticsUtils.getDestServiceTypeFromColumnName(qualifier);
        // Caller may be a user node, and user nodes may call nodes with the same application name but different service type.
        // To distinguish between these user nodes, append callee's service type to the application name.
        String callerApplicationName;
        if (registry.findServiceType(callerServiceType).isUser()) {
            callerApplicationName = ApplicationMapStatisticsUtils.getDestApplicationNameFromColumnNameForUser(qualifier, calleeServiceType);
        } else {
            callerApplicationName = ApplicationMapStatisticsUtils.getDestApplicationNameFromColumnName(qualifier);
        }
        return this.applicationFactory.createApplication(callerApplicationName, callerServiceType);
    }

    private Application readCalleeApplication(Buffer row) {
        String calleeApplicationName = row.read2PrefixedString();
        short calleeServiceType = row.readShort();

        return this.applicationFactory.createApplication(calleeApplicationName, calleeServiceType);
    }

    private byte[] getOriginalKey(byte[] rowKey) {
        return rowKeyDistributorByHashPrefix.getOriginalKey(rowKey);
    }
}
