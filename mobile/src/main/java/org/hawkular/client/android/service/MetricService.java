/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.client.android.service;

import org.hawkular.client.android.backend.model.Metric;
import org.hawkular.client.android.backend.model.MetricBucket;
import org.hawkular.client.android.backend.model.Trigger;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

/**
 * Created by pallavi on 25/06/17.
 */

public interface MetricService {

    @GET("hawkular/inventory/traversal/e;{e}/{r}/rl;incorporates/type=m")
    Call<List<Metric>> get();

    @GET
    Call<List<MetricBucket>> getMetricData(
            @Url String url
    );

    @GET("metrics/strings/tags/module:inventory,feed:*")
    Call<List<Metric>> getMetricFromFeed();

}

