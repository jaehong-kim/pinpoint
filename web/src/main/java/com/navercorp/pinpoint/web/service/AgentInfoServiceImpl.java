
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

package com.navercorp.pinpoint.web.service;

import com.navercorp.pinpoint.common.server.util.time.BackwardRangeSplitter;
import com.navercorp.pinpoint.common.server.util.time.DateTimeUtils;
import com.navercorp.pinpoint.common.server.util.time.Range;
import com.navercorp.pinpoint.common.server.util.time.RangeSplitter;
import com.navercorp.pinpoint.web.dao.AgentInfoDao;
import com.navercorp.pinpoint.web.dao.AgentLifeCycleDao;
import com.navercorp.pinpoint.web.dao.ApplicationIndexDao;
import com.navercorp.pinpoint.web.filter.agent.AgentEventFilter;
import com.navercorp.pinpoint.web.hyperlink.HyperLinkFactory;
import com.navercorp.pinpoint.web.service.component.ActiveAgentValidator;
import com.navercorp.pinpoint.web.service.stat.AgentWarningStatService;
import com.navercorp.pinpoint.web.vo.AgentEvent;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.agent.AgentAndStatus;
import com.navercorp.pinpoint.web.vo.agent.AgentInfo;
import com.navercorp.pinpoint.web.vo.agent.AgentInfoFilter;
import com.navercorp.pinpoint.web.vo.agent.AgentInfoFilters;
import com.navercorp.pinpoint.web.vo.agent.AgentStatus;
import com.navercorp.pinpoint.web.vo.agent.AgentStatusAndLink;
import com.navercorp.pinpoint.web.vo.agent.AgentStatusFilter;
import com.navercorp.pinpoint.web.vo.agent.AgentStatusQuery;
import com.navercorp.pinpoint.web.vo.agent.DetailedAgentAndStatus;
import com.navercorp.pinpoint.web.vo.agent.DetailedAgentInfo;
import com.navercorp.pinpoint.web.vo.timeline.inspector.AgentEventTimeline;
import com.navercorp.pinpoint.web.vo.timeline.inspector.AgentEventTimelineBuilder;
import com.navercorp.pinpoint.web.vo.timeline.inspector.AgentStatusTimeline;
import com.navercorp.pinpoint.web.vo.timeline.inspector.AgentStatusTimelineBuilder;
import com.navercorp.pinpoint.web.vo.timeline.inspector.AgentStatusTimelineSegment;
import com.navercorp.pinpoint.web.vo.timeline.inspector.InspectorTimeline;
import com.navercorp.pinpoint.web.vo.tree.AgentsMapByApplication;
import com.navercorp.pinpoint.web.vo.tree.AgentsMapByHost;
import com.navercorp.pinpoint.web.vo.tree.ApplicationAgentHostList;
import com.navercorp.pinpoint.web.vo.tree.SortByAgentInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author netspider
 * @author HyunGil Jeong
 */
@Service
public class AgentInfoServiceImpl implements AgentInfoService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final AgentEventService agentEventService;

    private final AgentWarningStatService agentWarningStatService;

    private final ApplicationIndexDao applicationIndexDao;

    private final AgentInfoDao agentInfoDao;

    private final AgentLifeCycleDao agentLifeCycleDao;

    private final HyperLinkFactory hyperLinkFactory;
    private final ActiveAgentValidator activeAgentValidator;


    public AgentInfoServiceImpl(AgentEventService agentEventService,
                                AgentWarningStatService agentWarningStatService,
                                ApplicationIndexDao applicationIndexDao,
                                AgentInfoDao agentInfoDao,
                                AgentLifeCycleDao agentLifeCycleDao,
                                ActiveAgentValidator activeAgentValidator,
                                HyperLinkFactory hyperLinkFactory) {
        this.agentEventService = Objects.requireNonNull(agentEventService, "agentEventService");
        this.agentWarningStatService = Objects.requireNonNull(agentWarningStatService, "agentWarningStatService");
        this.applicationIndexDao = Objects.requireNonNull(applicationIndexDao, "applicationIndexDao");
        this.agentInfoDao = Objects.requireNonNull(agentInfoDao, "agentInfoDao");
        this.agentLifeCycleDao = Objects.requireNonNull(agentLifeCycleDao, "agentLifeCycleDao");
        this.activeAgentValidator = Objects.requireNonNull(activeAgentValidator, "activeAgentValidator");
        this.hyperLinkFactory = Objects.requireNonNull(hyperLinkFactory, "hyperLinkFactory");
    }

    @Override
    public AgentsMapByApplication<AgentAndStatus> getAllAgentsList(AgentStatusFilter filter, Range range) {
        Objects.requireNonNull(filter, "filter");

        List<Application> applications = applicationIndexDao.selectAllApplicationNames();
        List<AgentAndStatus> agents = new ArrayList<>();
        for (Application application : applications) {
            agents.addAll(getAgentsByApplicationName(application.getName(), range.getTo()));
        }

        return AgentsMapByApplication.newAgentAndStatusMap(
                filter,
                agents
        );
    }

    @Override
    public AgentsMapByApplication<DetailedAgentInfo> getAllAgentsStatisticsList(AgentStatusFilter filter, Range range) {
        Objects.requireNonNull(filter, "filter");

        List<Application> applications = applicationIndexDao.selectAllApplicationNames();
        List<DetailedAgentAndStatus> agents = new ArrayList<>();
        for (Application application : applications) {
            agents.addAll(getDetailedAgentsByApplicationName(application.getName(), range.getTo()));
        }

        return AgentsMapByApplication.newDetailedAgentInfoMap(
                filter,
                agents
        );
    }

    @Override
    public AgentsMapByHost getAgentsListByApplicationName(AgentStatusFilter agentStatusFilter,
                                                          String applicationName,
                                                          Range range,
                                                          SortByAgentInfo.Rules sortBy) {
        return getAgentsListByApplicationName(agentStatusFilter, AgentInfoFilters.acceptAll(), applicationName, range, sortBy);
    }

    @Override
    public AgentsMapByHost getAgentsListByApplicationName(AgentStatusFilter agentStatusFilter,
                                                          AgentInfoFilter agentInfoPredicate,
                                                          String applicationName,
                                                          Range range,
                                                          SortByAgentInfo.Rules sortBy) {
        Objects.requireNonNull(agentStatusFilter, "agentStatusFilter");
        Objects.requireNonNull(agentInfoPredicate, "agentInfoPredicate");
        Objects.requireNonNull(applicationName, "applicationName");

        Set<AgentAndStatus> agentInfoAndStatuses = getAgentsByApplicationName(applicationName, range.getTo());
        if (agentInfoAndStatuses.isEmpty()) {
            logger.warn("agent list is empty for application:{}", applicationName);
        }

        AgentsMapByHost agentsMapByHost = AgentsMapByHost.newAgentsMapByHost(
                agentAndStatus -> isActiveAgentPredicate(agentAndStatus, agentInfoPredicate, agentStatusFilter, range),
                SortByAgentInfo.comparing(AgentStatusAndLink::getAgentInfo, sortBy.getRule()),
                hyperLinkFactory,
                agentInfoAndStatuses
        );

        final int totalAgentCount = agentsMapByHost.size();
        if (logger.isInfoEnabled()) {
            logger.info("getAgentsMapByHostname size:{}", totalAgentCount);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("getAgentsMapByHostname size:{} data:{}", totalAgentCount, agentsMapByHost);
        }
        return agentsMapByHost;
    }

    private boolean isActiveAgentPredicate(AgentAndStatus agentAndStatus,
                                           AgentInfoFilter agentInfoPredicate,
                                           Predicate<AgentStatus> agentStatusFilter,
                                           Range range) {
        logger.trace("isActiveAgentPredicate {}", agentAndStatus);
        AgentInfo agentInfo = agentAndStatus.getAgentInfo();
        if (agentInfoPredicate.test(agentInfo)) {
            logger.trace("agentInfoPredicate=true");
        }
        if (agentStatusFilter.test(agentAndStatus.getStatus())) {
            logger.trace("agentStatusFilter=true");
            return true;
        }
        Application agent = new Application(agentInfo.getAgentId(), agentInfo.getServiceType());
        String agentVersion = agentInfo.getAgentVersion();
        if (activeAgentValidator.isActiveAgent(agent, agentVersion, range)) {
            return true;
        }
        logger.trace("isActiveAgentPredicate=false");
        return false;
    }


    @Override
    public ApplicationAgentHostList getApplicationAgentHostList(int offset, int limit, Period durationDays) {
        if (offset <= 0) {
            throw new IllegalArgumentException("offset must be greater than 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        Objects.requireNonNull(durationDays, "durationDays");

        return getApplicationAgentHostList0(offset, limit, durationDays);
    }

    private ApplicationAgentHostList getApplicationAgentHostList0(int offset, int limit, Period durationDays) {
        List<String> applicationNameList = getApplicationNameList(applicationIndexDao.selectAllApplicationNames());
        if (offset > applicationNameList.size()) {
            ApplicationAgentHostList.Builder builder = newBuilder(offset, offset, applicationNameList.size());
            return builder.build();
        }

        final long timeStamp = System.currentTimeMillis();

        final int startIndex = offset - 1;
        final int endIndex = Math.min(startIndex + limit, applicationNameList.size());

        ApplicationAgentHostList.Builder builder = newBuilder(offset, endIndex, applicationNameList.size());
        for (int i = startIndex; i < endIndex; i++) {
            String applicationName = applicationNameList.get(i);

            List<String> agentIdList = getAgentIdList(applicationName, durationDays);
            List<AgentInfo> agentInfoList = this.agentInfoDao.getSimpleAgentInfos(agentIdList, timeStamp);
            builder.addAgentInfo(applicationName, agentInfoList);
        }
        return builder.build();
    }

    @Override
    public ApplicationAgentHostList getApplicationAgentHostList(int offset, int limit, int durationHours, List<Application> applicationList, Predicate<AgentInfo> agentInfoPredicate) {
        List<String> applicationNameList = getApplicationNameList(applicationList);
        return getApplicationAgentHostList2(offset, limit, durationHours, applicationNameList, agentInfoPredicate);
    }

    private ApplicationAgentHostList getApplicationAgentHostList2(int offset, int limit, int durationHours, List<String> applicationNameList, Predicate<AgentInfo> agentInfoPredicate) {
        if (offset > applicationNameList.size()) {
            ApplicationAgentHostList.Builder builder = newBuilder(offset, offset, applicationNameList.size());
            return builder.build();
        }
        final int startIndex = offset - 1;
        final int endIndex = Math.min(startIndex + limit, applicationNameList.size());

        ApplicationAgentHostList.Builder builder = newBuilder(offset, endIndex, applicationNameList.size());
        for (int i = startIndex; i < endIndex; i++) {
            String applicationName = applicationNameList.get(i);

            List<AgentInfo> agentInfoList = getAgentInfoList(applicationName, durationHours, agentInfoPredicate);

            builder.addAgentInfo(applicationName, agentInfoList);
        }
        return builder.build();
    }

    private List<AgentInfo> getAgentInfoList(String applicationName, int durationHours, Predicate<AgentInfo> agentInfoPredicate) {
        long currentTime = System.currentTimeMillis();
        List<String> agentIds = new ArrayList<>(new HashSet<>(this.applicationIndexDao.selectAgentIds(applicationName)));
        List<AgentInfo> agentInfoList = this.agentInfoDao.getSimpleAgentInfos(agentIds, currentTime);
        List<AgentInfo> filteredAgentInfoList = filterAgentInfo(agentInfoPredicate, agentInfoList);
        if (CollectionUtils.isEmpty(agentIds)) {
            return Collections.emptyList();
        }
        if (durationHours <= 0) {
            return filteredAgentInfoList;
        }
        long from = currentTime - TimeUnit.HOURS.toMillis(durationHours);
        RangeSplitter rangeSplitter = new BackwardRangeSplitter();
        List<Range> ranges = rangeSplitter.splitRange(from, currentTime);

        List<AgentInfo> result = new ArrayList<>();
        for (AgentInfo agentInfo : filteredAgentInfoList) {
            Application agent = new Application(agentInfo.getAgentId(), agentInfo.getServiceType());
            if (activeAgentValidator.isActiveAgent(agent, agentInfo.getAgentVersion(), ranges)) {
                result.add(agentInfo);
            }
        }
        return result;
    }

    private List<AgentInfo> filterAgentInfo(Predicate<AgentInfo> agentInfoPredicate, List<AgentInfo> agentInfoList) {
        if (agentInfoPredicate == null) {
            return agentInfoList;
        }

        List<AgentInfo> result = new ArrayList<>();
        for (AgentInfo agentInfo : agentInfoList) {
            if (agentInfoPredicate.test(agentInfo)) {
                result.add(agentInfo);
            }
        }
        return result;
    }

    private ApplicationAgentHostList.Builder newBuilder(int offset, int endIndex, int totalApplications) {
        return ApplicationAgentHostList.newBuilder(offset, endIndex, totalApplications);
    }

    private List<String> getAgentIdList(String applicationName, Period durationDays) {
        List<String> agentIds = this.applicationIndexDao.selectAgentIds(applicationName);
        if (CollectionUtils.isEmpty(agentIds)) {
            return Collections.emptyList();
        }
        if (durationDays.isNegative()) {
            return agentIds;
        }

        Instant now = DateTimeUtils.epochMilli();
        Instant before = now.minus(Duration.ofHours(1));
        Range fastRange = Range.between(before, now);

        Instant queryFrom = now.minus(durationDays);
        Instant queryTo = before.plusMillis(1);
        Range queryRange = Range.between(queryFrom, queryTo);

        List<String> activeAgentIdList = new ArrayList<>();
        for (String agentId : agentIds) {
            // FIXME This needs to be done with a more accurate information.
            // If at any time a non-java agent is introduced, or an agent that does not collect jvm data,
            // this will fail
            boolean dataExists = activeAgentValidator.isActiveAgent(agentId, fastRange);
            if (dataExists) {
                activeAgentIdList.add(agentId);
                continue;
            }

            dataExists = activeAgentValidator.isActiveAgent(agentId, queryRange);
            if (dataExists) {
                activeAgentIdList.add(agentId);
            }
        }
        return activeAgentIdList;
    }

    private List<String> getApplicationNameList(List<Application> applications) {
        return applications.stream()
                .map(Application::getName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public Set<AgentAndStatus> getAgentsByApplicationName(String applicationName, long timestamp) {
        List<AgentInfo> agentInfos = this.getAgentsByApplicationNameWithoutStatus0(applicationName, timestamp);

        List<AgentAndStatus> result = new ArrayList<>(agentInfos.size());

        AgentStatusQuery query = AgentStatusQuery.buildQuery(agentInfos, Instant.ofEpochMilli(timestamp));
        List<Optional<AgentStatus>> agentStatus = this.agentLifeCycleDao.getAgentStatus(query);
        for (int i = 0; i < agentStatus.size(); i++) {
            Optional<AgentStatus> status = agentStatus.get(i);
            AgentInfo agentInfo = agentInfos.get(i);
            result.add(new AgentAndStatus(agentInfo, status.orElse(null)));
        }

        return new HashSet<>(result);
    }


    @Override
    public Set<AgentInfo> getAgentsByApplicationNameWithoutStatus(String applicationName, long timestamp) {
        List<AgentInfo> agentInfos = getAgentsByApplicationNameWithoutStatus0(applicationName, timestamp);
        return new HashSet<>(agentInfos);
    }

    public List<AgentInfo> getAgentsByApplicationNameWithoutStatus0(String applicationName, long timestamp) {
        Objects.requireNonNull(applicationName, "applicationName");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }

        List<String> agentIds = this.applicationIndexDao.selectAgentIds(applicationName);
        List<AgentInfo> agentInfos = this.agentInfoDao.getSimpleAgentInfos(agentIds, timestamp);

        return agentInfos.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

    }

    public Set<DetailedAgentAndStatus> getDetailedAgentsByApplicationName(String applicationName, long timestamp) {
        List<DetailedAgentInfo> agentInfos = this.getDetailedAgentsByApplicationNameWithoutStatus0(applicationName, timestamp);

        List<DetailedAgentAndStatus> result = new ArrayList<>(agentInfos.size());

        AgentStatusQuery query = AgentStatusQuery.buildGenericQuery(agentInfos, DetailedAgentInfo::getAgentInfo, Instant.ofEpochMilli(timestamp));
        List<Optional<AgentStatus>> agentStatus = this.agentLifeCycleDao.getAgentStatus(query);

        for (int i = 0; i < agentStatus.size(); i++) {
            Optional<AgentStatus> status = agentStatus.get(i);
            DetailedAgentInfo agentInfo = agentInfos.get(i);
            result.add(new DetailedAgentAndStatus(agentInfo, status.orElse(null)));
        }

        return new HashSet<>(result);
    }

    public List<DetailedAgentInfo> getDetailedAgentsByApplicationNameWithoutStatus0(String applicationName, long timestamp) {
        Objects.requireNonNull(applicationName, "applicationName");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }

        List<String> agentIds = this.applicationIndexDao.selectAgentIds(applicationName);
        List<DetailedAgentInfo> agentInfos = this.agentInfoDao.getDetailedAgentInfos(agentIds, timestamp, false, true);

        return agentInfos.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public AgentAndStatus getAgentInfo(String agentId, long timestamp) {
        Objects.requireNonNull(agentId, "agentId");

        AgentInfo agentInfo = getAgentInfoWithoutStatus(agentId, timestamp);
        if (agentInfo == null) {
            return null;
        }

        Optional<AgentStatus> agentStatus = this.agentLifeCycleDao.getAgentStatus(agentInfo.getAgentId(), agentInfo.getStartTimestamp(), timestamp);
        return new AgentAndStatus(agentInfo, agentStatus.orElse(null));
    }

    @Override
    public DetailedAgentAndStatus getDetailedAgentInfo(String agentId, long timestamp) {
        Objects.requireNonNull(agentId, "agentId");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }

        DetailedAgentInfo detailedAgentInfo = this.agentInfoDao.getDetailedAgentInfo(agentId, timestamp);
        if (detailedAgentInfo == null) {
            return null;
        }
        AgentInfo agentInfo = detailedAgentInfo.getAgentInfo();

        Optional<AgentStatus> agentStatus = this.agentLifeCycleDao.getAgentStatus(agentInfo.getAgentId(), agentInfo.getStartTimestamp(), timestamp);
        return new DetailedAgentAndStatus(detailedAgentInfo, agentStatus.orElse(null));

    }

    @Override
    public AgentInfo getAgentInfoWithoutStatus(String agentId, long timestamp) {
        Objects.requireNonNull(agentId, "agentId");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }
        return this.agentInfoDao.getAgentInfo(agentId, timestamp);
    }

    @Override
    public AgentInfo getAgentInfoWithoutStatus(String agentId, long agentStartTime, int deltaTimeInMilliSeconds) {
        Objects.requireNonNull(agentId, "agentId");

        return this.agentInfoDao.getAgentInfo(agentId, agentStartTime, deltaTimeInMilliSeconds);
    }

    @Override
    public AgentStatus getAgentStatus(String agentId, long timestamp) {
        Objects.requireNonNull(agentId, "agentId");

        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }
        return this.agentLifeCycleDao.getAgentStatus(agentId, timestamp);
    }

    @Override
    public List<Optional<AgentStatus>> getAgentStatus(AgentStatusQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.getQueryTimestamp() < 0) {
            throw new IllegalArgumentException("timestamp must not be less than 0");
        }
        return this.agentLifeCycleDao.getAgentStatus(query);
    }


    @Override
    public InspectorTimeline getAgentStatusTimeline(String agentId, Range range, int... excludeAgentEventTypeCodes) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(range, "range");

        AgentStatus initialStatus = getAgentStatus(agentId, range.getFrom());
        List<AgentEvent> agentEvents = agentEventService.getAgentEvents(agentId, range);

        List<AgentStatusTimelineSegment> warningStatusTimelineSegmentList = agentWarningStatService.select(agentId, range);

        AgentStatusTimelineBuilder agentStatusTimelinebuilder = new AgentStatusTimelineBuilder(range, initialStatus, agentEvents, warningStatusTimelineSegmentList);
        AgentStatusTimeline agentStatusTimeline = agentStatusTimelinebuilder.build();

        AgentEventTimelineBuilder agentEventTimelineBuilder = new AgentEventTimelineBuilder(range);
        agentEventTimelineBuilder.from(agentEvents);
        agentEventTimelineBuilder.addFilter(new AgentEventFilter.ExcludeFilter(excludeAgentEventTypeCodes));
        AgentEventTimeline agentEventTimeline = agentEventTimelineBuilder.build();

        return new InspectorTimeline(agentStatusTimeline, agentEventTimeline);
    }

    @Override
    public boolean isExistAgentId(String agentId) {
        Objects.requireNonNull(agentId, "agentId");

        AgentInfo agentInfo = getAgentInfoWithoutStatus(agentId, System.currentTimeMillis());
        return agentInfo != null;
    }

}
