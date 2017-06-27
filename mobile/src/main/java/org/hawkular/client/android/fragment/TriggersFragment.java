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
package org.hawkular.client.android.fragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.hawkular.client.android.R;
import org.hawkular.client.android.activity.TriggerDetailActivity;
import org.hawkular.client.android.adapter.TriggersAdapter;
import org.hawkular.client.android.backend.BackendClient;
import org.hawkular.client.android.backend.model.Resource;
import org.hawkular.client.android.backend.model.Trigger;
import org.hawkular.client.android.util.ColorSchemer;
import org.hawkular.client.android.util.ErrorUtil;
import org.hawkular.client.android.util.Fragments;
import org.hawkular.client.android.util.Intents;
import org.hawkular.client.android.util.ViewDirector;
import org.jboss.aerogear.android.pipe.callback.AbstractSupportFragmentCallback;
import org.jboss.aerogear.android.store.DataManager;
import org.jboss.aerogear.android.store.generator.IdGenerator;
import org.jboss.aerogear.android.store.sql.SQLStore;
import org.jboss.aerogear.android.store.sql.SQLStoreConfiguration;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Triggers fragment.
 *
 * Displays triggers as a list.
 */

public class TriggersFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        TriggersAdapter.TriggerListener, SearchView.OnQueryTextListener {

    @BindView(R.id.list) RecyclerView recyclerView;
    @BindView(R.id.content) SwipeRefreshLayout contentLayout;

    ArrayList<Trigger> triggers;
    private boolean isTriggersFragmentAvailable;
    public SearchView searchView;
    public String searchText;
    public TriggersAdapter triggersAdapter;


    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);

        isTriggersFragmentAvailable = true;
        setUpBindings();
        setUpRefreshing();
        setUpTriggers();
        setUpList();
        setUpState(state);
        setUpMenu();
    }

    @Override
    public void onResume() {
        super.onResume();
        if( getArguments().getString("state").equalsIgnoreCase("From Favourite")) {
            setUpFavTriggers();
        } else {
            BackendClient.of(this).getRetroTriggers(new TriggersCallback(this));
        }
    }

    private void setUpState(Bundle state) {
        Icepick.restoreInstanceState(this, state);
    }

    private void setUpList() {
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);
    }

    private void setUpBindings() {
        ButterKnife.bind(this, getView());
    }

    private void setUpRefreshing() {
        contentLayout.setOnRefreshListener(this);
        contentLayout.setColorSchemeResources(ColorSchemer.getScheme());
    }

    private void setUpMenu() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onRefresh() {
        setUpTriggersForced();
    }

    private void setUpTriggersForced() {

        if( getArguments().getString("state").equalsIgnoreCase("From Favourite")) {
            setUpFavTriggers();
        }
        else {
            BackendClient.of(this).getRetroTriggers(new TriggersCallback(this));
        }
    }

    @OnClick(R.id.button_retry)
    public void setUpTriggers() {
        if (triggers == null) {
            showProgress();
            setUpTriggersForced();
        } else {
            setUpTriggers(triggers);
        }
    }

    private void showProgress() {
        ViewDirector.of(this).using(R.id.animator).show(R.id.progress);
    }

    private Resource getResource() {
        return getArguments().getParcelable(Fragments.Arguments.RESOURCE);
    }

    private void setUpTriggers(List<Trigger> triggers) {
        this.triggers = new ArrayList<>(triggers);

        sortTriggers(this.triggers);

        recyclerView.setAdapter(new TriggersAdapter(getActivity(), this, triggers));

        hideRefreshing();

        showList();
    }

    private void sortTriggers(List<Trigger> triggers) {
        Collections.sort(triggers, new TriggersComparator());
    }

    private void hideRefreshing() {
        contentLayout.setRefreshing(false);
    }

    private void showList() {
        ViewDirector.of(this).using(R.id.animator).show(R.id.content);
    }

    private void showMessage() {
        ViewDirector.of(this).using(R.id.animator).show(R.id.message);
    }

    private void setUpFavTriggers() {
        Context context = this.getActivity();
        SQLStore<Trigger> store = openStore(context);
        store.openSync();

        Collection<Trigger> array = store.readAll();
        triggers = new ArrayList<>(array);
        sortTriggers(this.triggers);
        recyclerView.setAdapter(new TriggersAdapter(getActivity(), this, triggers));
        hideRefreshing();
        if(triggers.isEmpty()){
            showMessage();
        }
        else{
            showList();
        }

        store.close();
    }

    private SQLStore<Trigger> openStore(Context context) {
        DataManager.config("FavouriteTriggers", SQLStoreConfiguration.class)
                .withContext(context)
                .withIdGenerator(new IdGenerator() {
                    @Override
                    public String generate() {
                        return UUID.randomUUID().toString();
                    }
                }).store(Trigger.class);
        return (SQLStore<Trigger>) DataManager.getStore("FavouriteTriggers");
    }

    private TriggersAdapter getTriggersAdapter() {
        return (TriggersAdapter) recyclerView.getAdapter();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.menu_search, menu);

        MenuItem item = menu.findItem(R.id.menu_search1);
        searchView = (SearchView) MenuItemCompat.getActionView(item);
        searchView.setOnQueryTextListener(this);
        if (searchText != null) {
            searchView.setQuery(searchText, false);
        }
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (triggers != null && triggers.size() != 0) {
            if (!TextUtils.isEmpty(query)) {
                ArrayList<Trigger> filteredMetrics = new ArrayList<>();
                filteredMetrics.clear();
                for (int i=0;i<triggers.size();i++) {
                    String alertID = triggers.get(i).getId().toLowerCase();
                    if (alertID.contains(query.toLowerCase())) {
                        filteredMetrics.add(triggers.get(i));
                    }
                }
                triggersAdapter = new TriggersAdapter(getActivity(), this, filteredMetrics);
                recyclerView.setAdapter(triggersAdapter);
                searchText = query;
            } else {
                triggersAdapter = new TriggersAdapter(getActivity(), this, this.triggers);
                recyclerView.setAdapter(triggersAdapter);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        searchView.clearFocus();
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);

        tearDownState(state);
    }

    private void tearDownState(Bundle state) {
        Icepick.saveInstanceState(this, state);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isTriggersFragmentAvailable = false;
    }

    @Override
    public void onTriggerToggleChanged(View TriggerView, int triggerPosition, boolean state) {
        Trigger updatedTrigger = this.triggers.get(triggerPosition);
        updatedTrigger.setEnabledStatus(state);
        if (state){
            Snackbar snackbar = Snackbar.make(getView(),R.string.trigger_on, Snackbar.LENGTH_SHORT);
            snackbar.show();
        }
        else {
            Snackbar snackbar = Snackbar.make(getView(),R.string.trigger_off, Snackbar.LENGTH_SHORT);
            snackbar.show();
        }

        BackendClient.of(TriggersFragment.this).updateRetroTrigger(updatedTrigger,new TriggerUpdateCallback());
    }

    @Override
    public void onTriggerTextClick(View triggerView, int triggerPosition) {
        Intent intent = new Intent(getActivity(), TriggerDetailActivity.class);
        Trigger trigger = getTriggersAdapter().getItem(triggerPosition);
        intent.putExtra(Intents.Extras.TRIGGER,trigger);
        startActivity(intent);
    }

    private static final class TriggersCallback implements Callback<List<Trigger>> {

        private TriggersFragment triggersFragment;

        public TriggersCallback(TriggersFragment triggersFragment) {
            this.triggersFragment = triggersFragment;
        }

        @Override public void onResponse(Call<List<Trigger>> call, Response<List<Trigger>> response) {
            if (response.isSuccessful()) {
                List<Trigger> triggers = response.body();
                if (getTriggersFragment().isTriggersFragmentAvailable) {
                    if (!triggers.isEmpty()) {
                        getTriggersFragment().setUpTriggers(triggers);
                    } else {
                        getTriggersFragment().showMessage();
                    }
                }
            }
        }

        @Override public void onFailure(Call<List<Trigger>> call, Throwable t) {
            Timber.d(t, "Triggers fetching failed.");

            if (getTriggersFragment().isTriggersFragmentAvailable) {
                ErrorUtil.showError(getTriggersFragment(),R.id.animator,R.id.error);
            }
        }

        public TriggersFragment getTriggersFragment() {
            return triggersFragment;
        }
    }

    private class TriggerUpdateCallback implements Callback{

        @Override
        public void onResponse(Call call, Response response) {
            Log.d("Update Trigger Response", response.message());
        }

        @Override
        public void onFailure(Call call, Throwable t) {

        }
    }

    private static final class TriggersComparator implements Comparator<Trigger> {
        @Override
        public int compare(Trigger leftTrigger, Trigger rightTrigger) {
            String leftTriggerDescription = leftTrigger.getId();
            String rightTriggerDescription = rightTrigger.getId();

            return leftTriggerDescription.compareTo(rightTriggerDescription);
        }
    }
}
