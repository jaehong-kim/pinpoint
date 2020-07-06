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

package com.navercorp.pinpoint.web.service;

import com.navercorp.pinpoint.web.dao.MapResponseDao;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.ResponseTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author jaehong.kim
 */
@Service
public class CompactMapResponseService  implements MapResponseService {
    private final MapResponseDao mapResponseDao;

    public CompactMapResponseService(@Qualifier("hbaseMapResponseTimeCompactDao") MapResponseDao mapResponseDao) {
        this.mapResponseDao = mapResponseDao;
    }

    @Override
    public List<ResponseTime> selectResponseTime(Application application, Range range) {
        return mapResponseDao.selectResponseTime(application, range);
    }
}