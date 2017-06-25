package org.hawkular.client.android.service;

import org.hawkular.client.android.backend.model.Metric;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Created by pallavi on 26/06/17.
 */

public interface MetricFromFeed {

    @GET("metrics/strings/tags/module:inventory,feed:*")
    Call<List<Metric>> get();
}
