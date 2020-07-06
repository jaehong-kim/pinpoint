package com.navercorp.pinpoint.web.service;

import com.navercorp.pinpoint.web.applicationmap.ApplicationMap;
import com.navercorp.pinpoint.web.applicationmap.ApplicationMapBuilder;
import com.navercorp.pinpoint.web.applicationmap.ApplicationMapBuilderFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.DefaultNodeHistogramFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.NodeHistogramFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.datasource.MapResponseNodeHistogramDataSource;
import com.navercorp.pinpoint.web.applicationmap.appender.histogram.datasource.WasNodeHistogramDataSource;
import com.navercorp.pinpoint.web.applicationmap.appender.server.DefaultServerInstanceListFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.server.ServerInstanceListFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.server.datasource.ServerInstanceListDataSource;
import com.navercorp.pinpoint.web.applicationmap.link.LinkType;
import com.navercorp.pinpoint.web.applicationmap.nodes.NodeType;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataDuplexMap;
import com.navercorp.pinpoint.web.dao.MapResponseDao;
import com.navercorp.pinpoint.web.security.ServerMapDataFilter;
import com.navercorp.pinpoint.web.service.map.LinkSelector;
import com.navercorp.pinpoint.web.service.map.LinkSelectorFactory;
import com.navercorp.pinpoint.web.service.map.LinkSelectorType;
import com.navercorp.pinpoint.web.service.map.processor.LinkDataMapProcessor;
import com.navercorp.pinpoint.web.service.map.processor.WasOnlyProcessor;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.SearchOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * @author netspider
 * @author emeroad
 * @author minwoo.jung
 * @author HyunGil Jeong
 * @author jaehong.kim
 */
public abstract class BaseMapServiceImpl implements MapService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final LinkSelectorFactory linkSelectorFactory;
    private final ServerInstanceListDataSource serverInstanceListDataSource;
    private final MapResponseService mapResponseService;
    private final ServerMapDataFilter serverMapDataFilter;
    private final ApplicationMapBuilderFactory applicationMapBuilderFactory;

    public BaseMapServiceImpl(LinkSelectorFactory linkSelectorFactory,
                              ServerInstanceListDataSource serverInstanceListDataSource,
                              MapResponseService mapResponseService,
                              Optional<ServerMapDataFilter> serverMapDataFilter,
                              ApplicationMapBuilderFactory applicationMapBuilderFactory) {
        this.linkSelectorFactory = Objects.requireNonNull(linkSelectorFactory, "linkSelectorFactory");
        this.serverInstanceListDataSource = Objects.requireNonNull(serverInstanceListDataSource, "serverInstanceListDataSource");
        this.mapResponseService = Objects.requireNonNull(mapResponseService, "mapResponseService");
        this.serverMapDataFilter = Objects.requireNonNull(serverMapDataFilter, "serverMapDataFilter").orElse(null);
        this.applicationMapBuilderFactory = Objects.requireNonNull(applicationMapBuilderFactory, "applicationMapBuilderFactory");
    }

    /**
     * Used in the main UI - draws the server map by querying the timeslot by time.
     */
    @Override
    public ApplicationMap selectApplicationMap(Application sourceApplication, Range range, SearchOption searchOption, NodeType nodeType, LinkType linkType) {
        if (sourceApplication == null) {
            throw new NullPointerException("sourceApplication");
        }
        if (range == null) {
            throw new NullPointerException("range");
        }
        logger.debug("SelectApplicationMap");

        StopWatch watch = new StopWatch("ApplicationMap");
        watch.start("ApplicationMap Hbase Io Fetch(Caller,Callee) Time");

        LinkSelectorType linkSelectorType = searchOption.getLinkSelectorType();
        int callerSearchDepth = searchOption.getCallerSearchDepth();
        int calleeSearchDepth = searchOption.getCalleeSearchDepth();

        LinkDataMapProcessor callerLinkDataMapProcessor = LinkDataMapProcessor.NO_OP;
        if (searchOption.isWasOnly()) {
            callerLinkDataMapProcessor = new WasOnlyProcessor();
        }
        LinkDataMapProcessor calleeLinkDataMapProcessor = LinkDataMapProcessor.NO_OP;
        LinkSelector linkSelector = linkSelectorFactory.createLinkSelector(linkSelectorType, callerLinkDataMapProcessor, calleeLinkDataMapProcessor);
        LinkDataDuplexMap linkDataDuplexMap = linkSelector.select(Collections.singletonList(sourceApplication), range, callerSearchDepth, calleeSearchDepth);
        watch.stop();

        watch.start("ApplicationMap MapBuilding(Response) Time");

        ApplicationMapBuilder builder = createApplicationMapBuilder(range, nodeType, linkType);
        ApplicationMap map = builder.build(linkDataDuplexMap);
        if (map.getNodes().isEmpty()) {
            map = builder.build(sourceApplication);
        }
        watch.stop();
        if (logger.isInfoEnabled()) {
            logger.info("ApplicationMap BuildTime: {}", watch.prettyPrint());
        }
        if(serverMapDataFilter != null) {
            map = serverMapDataFilter.dataFiltering(map);
        }
        return map;
    }

    private ApplicationMapBuilder createApplicationMapBuilder(Range range, NodeType nodeType, LinkType linkType) {
        ApplicationMapBuilder builder = applicationMapBuilderFactory.createApplicationMapBuilder(range);
        builder.nodeType(nodeType);
        builder.linkType(linkType);

        WasNodeHistogramDataSource wasNodeHistogramDataSource = new MapResponseNodeHistogramDataSource(mapResponseService);
        NodeHistogramFactory nodeHistogramFactory = new DefaultNodeHistogramFactory(wasNodeHistogramDataSource);
        builder.includeNodeHistogram(nodeHistogramFactory);

        ServerInstanceListFactory serverInstanceListFactory = new DefaultServerInstanceListFactory(serverInstanceListDataSource);
        builder.includeServerInfo(serverInstanceListFactory);
        return builder;
    }
}