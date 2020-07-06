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

package com.navercorp.pinpoint.web.controller;

import com.navercorp.pinpoint.common.profiler.sql.DefaultSqlParser;
import com.navercorp.pinpoint.common.profiler.sql.OutputParameterParser;
import com.navercorp.pinpoint.common.profiler.sql.SqlParser;
import com.navercorp.pinpoint.common.profiler.util.TransactionId;
import com.navercorp.pinpoint.common.profiler.util.TransactionIdUtils;
import com.navercorp.pinpoint.web.applicationmap.ApplicationMap;
import com.navercorp.pinpoint.web.applicationmap.histogram.LoadHistogramFormat;
import com.navercorp.pinpoint.web.calltree.span.CallTreeIterator;
import com.navercorp.pinpoint.web.config.LogConfiguration;
import com.navercorp.pinpoint.web.service.FilteredMapService;
import com.navercorp.pinpoint.web.service.SpanResult;
import com.navercorp.pinpoint.web.service.SpanService;
import com.navercorp.pinpoint.web.service.TransactionInfoService;
import com.navercorp.pinpoint.web.util.DefaultMongoJsonParser;
import com.navercorp.pinpoint.web.util.MongoJsonParser;
import com.navercorp.pinpoint.web.util.OutputParameterMongoJsonParser;
import com.navercorp.pinpoint.web.view.TransactionInfoViewModel;
import com.navercorp.pinpoint.web.vo.callstacks.RecordSet;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * @author emeroad
 * @author jaehong.kim
 */
@Controller
public class BusinessTransactionController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SpanService spanService;
    @Autowired
    private TransactionInfoService transactionInfoService;
    @Autowired
    @Qualifier("defaultFilteredMapServiceImpl")
    private FilteredMapService defaultFilteredMapService;
    @Autowired
    @Qualifier("compactFilteredMapServiceImpl")
    private FilteredMapService compactFilteredMapService;
    @Autowired
    private LogConfiguration logConfiguration;
    private final SqlParser sqlParser = new DefaultSqlParser();
    private final OutputParameterParser parameterParser = new OutputParameterParser();
    private final MongoJsonParser mongoJsonParser = new DefaultMongoJsonParser();
    private final OutputParameterMongoJsonParser parameterJsonParser = new OutputParameterMongoJsonParser();

    /**
     * info lookup for a selected transaction
     *
     * @param traceIdParam
     * @param focusTimestamp
     * @return
     */
    @RequestMapping(value = "/transactionInfo", method = RequestMethod.GET)
    @ResponseBody
    public TransactionInfoViewModel transactionInfo(@RequestParam("traceId") String traceIdParam,
                                                    @RequestParam(value = "focusTimestamp", required = false, defaultValue = "0") long focusTimestamp,
                                                    @RequestParam(value = "agentId", required = false) String agentId,
                                                    @RequestParam(value = "spanId", required = false, defaultValue = "-1") long spanId,
                                                    @RequestParam(value = "v", required = false, defaultValue = "0") int viewVersion) {
        logger.debug("GET /transactionInfo params {traceId={}, focusTimestamp={}, agentId={}, spanId={}, v={}}", traceIdParam, focusTimestamp, agentId, spanId, viewVersion);

        final TransactionId transactionId = TransactionIdUtils.parseTransactionId(traceIdParam);

        // select spans
        final SpanResult spanResult = this.spanService.selectSpan(transactionId, focusTimestamp);
        final CallTreeIterator callTreeIterator = spanResult.getCallTree();

        // application map
        ApplicationMap map = defaultFilteredMapService.selectApplicationMap(transactionId, viewVersion);
        RecordSet recordSet = this.transactionInfoService.createRecordSet(callTreeIterator, focusTimestamp, agentId, spanId);

        TransactionInfoViewModel result = new TransactionInfoViewModel(transactionId, spanId, map.getNodes(), map.getLinks(), recordSet, spanResult.getTraceState(), logConfiguration);
        return result;
    }

    /**
     * info lookup for a selected transaction
     *
     * @param traceIdParam
     * @param focusTimestamp
     * @return
     */
    @RequestMapping(value = "/transactionInfoV2", method = RequestMethod.GET)
    @ResponseBody
    public TransactionInfoViewModel transactionInfoV2(@RequestParam("traceId") String traceIdParam,
                                                    @RequestParam(value = "focusTimestamp", required = false, defaultValue = "0") long focusTimestamp,
                                                    @RequestParam(value = "agentId", required = false) String agentId,
                                                    @RequestParam(value = "spanId", required = false, defaultValue = "-1") long spanId,
                                                    @RequestParam(value = "v", required = false, defaultValue = "0") int viewVersion) {
        logger.debug("GET /transactionInfo params {traceId={}, focusTimestamp={}, agentId={}, spanId={}, v={}}", traceIdParam, focusTimestamp, agentId, spanId, viewVersion);

        final TransactionId transactionId = TransactionIdUtils.parseTransactionId(traceIdParam);
        // select spans
        final SpanResult spanResult = this.spanService.selectSpan(transactionId, focusTimestamp);
        final CallTreeIterator callTreeIterator = spanResult.getCallTree();
        // application map
        ApplicationMap map = compactFilteredMapService.selectApplicationMap(transactionId, viewVersion);
        RecordSet recordSet = this.transactionInfoService.createRecordSet(callTreeIterator, focusTimestamp, agentId, spanId);
        TransactionInfoViewModel result = new TransactionInfoViewModel(transactionId, spanId, map.getNodes(), map.getLinks(), recordSet, spanResult.getTraceState(), logConfiguration);
        result.setLoadHistogramFormat(LoadHistogramFormat.V2);

        return result;
    }

    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    @ResponseBody
    public String metaDataBind(@RequestParam("type") String type,
                                @RequestParam("metaData") String metaData,
                                @RequestParam("bind") String bind) {
        if (logger.isDebugEnabled()) {
            logger.debug("POST /bind params {metaData={}, bind={}}", metaData, bind);
        }

        if (metaData == null) {
            return "";
        }

        List<String> bindValues;
        String combinedResult = "";

        if (type.equals("sql")) {
            bindValues = parameterParser.parseOutputParameter(bind);
            combinedResult = sqlParser.combineBindValues(metaData, bindValues);
        } else if (type.equals("mongoJson")) {
            bindValues = parameterJsonParser.parseOutputParameter(bind);
            combinedResult = mongoJsonParser.combineBindValues(metaData, bindValues);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Combined result={}", combinedResult);
        }

        if (type.equals("mongoJson")) {
            return StringEscapeUtils.unescapeHtml4(combinedResult);
        }

        return StringEscapeUtils.escapeHtml4(combinedResult);
    }
}