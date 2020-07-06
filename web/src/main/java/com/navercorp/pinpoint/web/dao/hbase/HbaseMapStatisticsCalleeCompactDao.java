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

package com.navercorp.pinpoint.web.dao.hbase;

import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.HbaseOperations2;
import com.navercorp.pinpoint.common.hbase.ResultsExtractor;
import com.navercorp.pinpoint.common.hbase.RowMapper;
import com.navercorp.pinpoint.common.hbase.TableDescriptor;
import com.navercorp.pinpoint.common.profiler.util.ApplicationMapStatisticsUtils;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataMap;
import com.navercorp.pinpoint.web.dao.MapStatisticsCalleeDao;
import com.navercorp.pinpoint.web.mapper.MapStatisticsTimeWindowReducer;
import com.navercorp.pinpoint.web.mapper.RowMapReduceResultExtractor;
import com.navercorp.pinpoint.web.util.TimeWindow;
import com.navercorp.pinpoint.web.util.TimeWindowDownSampler;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.RangeFactory;
import com.sematext.hbase.wd.RowKeyDistributorByHashPrefix;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Scan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * @author netspider
 * @author emeroad
 * @author jaehong.kim
 */
@Repository
public class HbaseMapStatisticsCalleeCompactDao implements MapStatisticsCalleeDao {
    private static final int NUM_PARTITIONS = 32;
    private static final int SCAN_CACHE_SIZE = 40;

    private final HbaseOperations2 hbaseTemplate;
    private final RowMapper<LinkDataMap> mapStatisticsCalleeMapper;
    private final RangeFactory rangeFactory;
    private final RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix;
    private final TableDescriptor<HbaseColumnFamily.CallerStatMapCompact> descriptor;

    public HbaseMapStatisticsCalleeCompactDao(HbaseOperations2 hbaseTemplate,
                                              TableDescriptor<HbaseColumnFamily.CallerStatMapCompact> descriptor,
                                              @Qualifier("mapStatisticsCalleeCompactMapper") RowMapper<LinkDataMap> mapStatisticsCalleeMapper,
                                              RangeFactory rangeFactory,
                                              @Qualifier("statisticsCalleeCompactRowKeyDistributor") RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix) {
        this.hbaseTemplate = Objects.requireNonNull(hbaseTemplate, "hbaseTemplate");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.mapStatisticsCalleeMapper = Objects.requireNonNull(mapStatisticsCalleeMapper, "mapStatisticsCalleeMapper");
        this.rangeFactory = Objects.requireNonNull(rangeFactory, "rangeFactory");
        this.rowKeyDistributorByHashPrefix = Objects.requireNonNull(rowKeyDistributorByHashPrefix, "rowKeyDistributorByHashPrefix");
    }

    @Override
    public LinkDataMap selectCallee(Application calleeApplication, Range range) {
        Objects.requireNonNull(calleeApplication, "calleeApplication");
        Objects.requireNonNull(range, "range");

        final TimeWindow timeWindow = new TimeWindow(range, TimeWindowDownSampler.SAMPLER);
        // find distributed key - ver2.
        final Scan scan = createScan(calleeApplication, range, descriptor.getColumnFamilyName());
        final ResultsExtractor<LinkDataMap> resultExtractor = new RowMapReduceResultExtractor<>(mapStatisticsCalleeMapper, new MapStatisticsTimeWindowReducer(timeWindow));
        final TableName tableName = descriptor.getTableName();
        final LinkDataMap linkDataMap = hbaseTemplate.findParallel(tableName, scan, rowKeyDistributorByHashPrefix, resultExtractor, NUM_PARTITIONS);
        if (linkDataMap != null && linkDataMap.size() > 0) {
            return linkDataMap;
        }

        return new LinkDataMap();
    }


    private Scan createScan(Application application, Range range, byte[] family) {
        range = rangeFactory.createStatisticsRange(range);
        // start key is replaced by end key because timestamp has been reversed
        byte[] startKey = ApplicationMapStatisticsUtils.makeRowKey(application.getName(), application.getServiceTypeCode(), range.getTo());
        byte[] endKey = ApplicationMapStatisticsUtils.makeRowKey(application.getName(), application.getServiceTypeCode(), range.getFrom());

        Scan scan = new Scan();
        scan.setCaching(SCAN_CACHE_SIZE);
        scan.setStartRow(startKey);
        scan.setStopRow(endKey);
        scan.addFamily(family);
        scan.setId("ApplicationStatisticsScan");

        return scan;
    }
}