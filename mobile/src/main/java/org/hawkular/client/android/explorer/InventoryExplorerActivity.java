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
package org.hawkular.client.android.explorer;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.hawkular.client.android.R;
import org.hawkular.client.android.backend.BackendClient;
import org.hawkular.client.android.backend.model.Error;
import org.hawkular.client.android.backend.model.Feed;
import org.hawkular.client.android.backend.model.InventoryResponseBody;
import org.hawkular.client.android.backend.model.Metric;
import org.hawkular.client.android.backend.model.Operation;
import org.hawkular.client.android.backend.model.Resource;
import org.hawkular.client.android.explorer.holder.IconTreeItemHolder;
import org.hawkular.client.android.fragment.ConfirmOperationFragment;
import org.hawkular.client.android.util.Fragments;
import org.hawkular.client.android.util.Intents;
import org.jboss.aerogear.android.core.Callback;
import org.jboss.aerogear.android.pipe.callback.AbstractActivityCallback;
import org.jboss.aerogear.android.store.DataManager;
import org.jboss.aerogear.android.store.generator.IdGenerator;
import org.jboss.aerogear.android.store.sql.SQLStore;
import org.jboss.aerogear.android.store.sql.SQLStoreConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.unnamed.b.atv.model.TreeNode;
import com.unnamed.b.atv.view.AndroidTreeView;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Inventory Explorer activity.
 *
 * Manage explorer interaction and presentation.
 */

public class InventoryExplorerActivity extends AppCompatActivity {
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private AndroidTreeView tView;
    private TreeNode.BaseNodeViewHolder holder;
    private TreeNode root;
    private Callback<String> callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_explorer);

        callback = new PerformOperationCallback();

        setUpBindings();

        setUpToolbar();

        ViewGroup containerView = (ViewGroup) findViewById(R.id.container);

        root = TreeNode.root();

        holder = root.getViewHolder();

        tView = new AndroidTreeView(this, root);
        tView.setDefaultAnimation(true);
        tView.setDefaultContainerStyle(R.style.TreeNodeStyleCustom);
        tView.setDefaultViewHolder(IconTreeItemHolder.class);
        tView.setDefaultNodeClickListener(nodeClickListener);
        tView.setDefaultNodeLongClickListener(nodeLongClickListener);
        containerView.addView(tView.getView());

        BackendClient.of(this).getFeeds(new FeedsCallback());

        if (savedInstanceState != null) {
            String state = savedInstanceState.getString("tState");
            if (!TextUtils.isEmpty(state)) {
                tView.restoreState(state);
            }
        }

    }

    private void setUpBindings() {
        ButterKnife.bind(this);
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);

        if (getSupportActionBar() == null) {
            return;
        }

        getSupportActionBar().setTitle("Explorer");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    private void setUpFeeds(List<String> feeds) {
        for (String feed : feeds) {
            int icon = getResources().getIdentifier("drawable/" + "feed_icon", null, getPackageName());
            TreeNode newFeed = new TreeNode(new IconTreeItemHolder.IconTreeItem(
                    icon, IconTreeItemHolder.IconTreeItem.Type.FEED, feed, feed));
            tView.addNode(root, newFeed);
        }

    }

    private void setUpOperations(List<Operation> operations, TreeNode parent) {
        for (Operation operation : operations) {
            int icon = getResources().getIdentifier("drawable/" + "operation_icon", null, getPackageName());
            TreeNode newFeed = new TreeNode(new IconTreeItemHolder.IconTreeItem(
                    icon, IconTreeItemHolder.IconTreeItem.Type.OPERATION, operation.getId(), operation));
            tView.addNode(parent, newFeed);
        }

    }

    private void setUpResources(List<Resource> resources, TreeNode parent) {
        for (Resource resource : resources) {
            int icon = getResources().getIdentifier("drawable/" + "resource_icon", null, getPackageName());
            TreeNode newResource = new TreeNode(new IconTreeItemHolder.IconTreeItem(
                    icon, IconTreeItemHolder.IconTreeItem.Type.RESOURCE, resource.getId(), resource));
            tView.addNode(parent, newResource);
        }

    }

    private void setUpMetrics(List<Metric> metrics, TreeNode parent) {
        for (Metric metric : metrics) {
            int icon = getResources().getIdentifier("drawable/" + "metric_icon", null, getPackageName());
            TreeNode newMetric = new TreeNode(new IconTreeItemHolder.IconTreeItem(
                    icon, IconTreeItemHolder.IconTreeItem.Type.METRIC, metric.getName(), metric));
            tView.addNode(parent, newMetric);
        }

    }


    private InventoryExplorerActivity getInventoryExplorerActivity() {
        return this;
    }


    private TreeNode.TreeNodeClickListener nodeClickListener = new TreeNode.TreeNodeClickListener() {
        @Override
        public void onClick(TreeNode node, Object value) {
            IconTreeItemHolder.IconTreeItem item = (IconTreeItemHolder.IconTreeItem) value;
            if (item.type == IconTreeItemHolder.IconTreeItem.Type.FEED) {
                if (node.size() == 0) {
                    String path1;
                    String feed = (String) item.value;
                    path1 = "feed:"+ feed + ",type:r";

                    InventoryResponseBody body = new InventoryResponseBody("true","DESC",path1);
                    Log.d("Full path", path1);
                    BackendClient.of(getInventoryExplorerActivity()).getResourcesFromFeed(
                            new ResourcesCallback(node), body);
                }
            } else if (item.type == IconTreeItemHolder.IconTreeItem.Type.RESOURCE) {
                if (node.size() == 0) {
                    //BackendClient.of(getInventoryExplorerActivity()).getRecResourcesFromFeed(
                      //      new ResourcesCallback(node), (Resource) item.value);
                   // BackendClient.of(getInventoryExplorerActivity()).getRetroMetricsFromFeed(
                     //       new MetricsCallback(node), (Resource) item.value);
                    BackendClient.of(getInventoryExplorerActivity()).getOpreations(
                            new OperationsCallback(node), (Resource) item.value);
                }
            } else if (item.type == IconTreeItemHolder.IconTreeItem.Type.METRIC) {
                Intent intent = Intents.Builder.of(getApplicationContext()).buildMetricIntent((Metric) item.value);
                startActivity(intent);
            } else if (item.type == IconTreeItemHolder.IconTreeItem.Type.OPERATION) {
                Resource resource = (Resource) ((IconTreeItemHolder.IconTreeItem) node.getParent().getValue()).value;
                Operation operation = (Operation) ((IconTreeItemHolder.IconTreeItem) node.getValue()).value;

                ConfirmOperationFragment dialog = new ConfirmOperationFragment();
                Bundle bundle = new Bundle();
                bundle.putParcelable(Fragments.Arguments.RESOURCE, resource);
                bundle.putParcelable(Fragments.Arguments.OPERATION, operation);
                dialog.setArguments(bundle);
                dialog.setCallback(callback);
                dialog.show(getSupportFragmentManager(), "missiles");
            }
        }
    };

    private TreeNode.TreeNodeLongClickListener nodeLongClickListener = new TreeNode.TreeNodeLongClickListener() {
        @Override
        public boolean onLongClick(TreeNode node, final Object value) {
            final IconTreeItemHolder.IconTreeItem item = (IconTreeItemHolder.IconTreeItem) value;
            if (item.type == IconTreeItemHolder.IconTreeItem.Type.FEED) {

            } else if (item.type == IconTreeItemHolder.IconTreeItem.Type.RESOURCE) {

            } else if (item.type == IconTreeItemHolder.IconTreeItem.Type.METRIC) {
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(getInventoryExplorerActivity(), R.style.AlertDialogStyle);
                builder.setTitle("Hey");
                builder.setMessage("Do you want to add this Metric to Favourite list?");
                builder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                addMetricToFav((Metric)item.value);
                            }
                        });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
            return true;
        }
    };


    private void addMetricToFav(final Metric metric) {
        SQLStore<Metric> store = openStore(getApplicationContext());
        store.openSync();
        store.save(metric);
        Toast.makeText(getApplicationContext(), "Metric added to favourite", Toast.LENGTH_SHORT).show();

    }

    private SQLStore<Metric> openStore(Context context) {
        DataManager.config("Favourite", SQLStoreConfiguration.class)
                .withContext(context)
                .withIdGenerator(new IdGenerator() {
                    @Override
                    public String generate() {
                        return UUID.randomUUID().toString();
                    }
                }).store(Metric.class);
        return (SQLStore<Metric>) DataManager.getStore("Favourite");
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    private final class FeedsCallback implements retrofit2.Callback<Feed>{

        @Override
        public void onResponse(Call<Feed> call, Response<Feed> response) {


            Feed feed = response.body();

            if (response.isSuccessful()) {
                Log.d("response on feed","id is =" + feed.getFeed().get(0));
                getInventoryExplorerActivity().setUpFeeds(response.body().getFeed());
            }
        }

        @Override
        public void onFailure(Call<Feed> call, Throwable t) {
                Log.d("Fetching Failed", t.getMessage());
        }

        private InventoryExplorerActivity getInventoryExplorerActivity() {
            return InventoryExplorerActivity.this;
        }
    }

    private final class OperationsCallback extends AbstractActivityCallback<List<Operation>> {

        private TreeNode parent;

        OperationsCallback(TreeNode parent) {
            this.parent = parent;
        }

        @Override
        public void onSuccess(List<Operation> operations) {
            if (!operations.isEmpty()) {
                getInventoryExplorerActivity().setUpOperations(operations, parent);
            }
        }

        @Override
        public void onFailure(Exception e) {
            Timber.d("Resources fetching failed.");

        }

        private InventoryExplorerActivity getInventoryExplorerActivity() {
            return (InventoryExplorerActivity) getActivity();
        }
    }

    private final class ResourcesCallback implements retrofit2.Callback<List<Resource>> {

        private TreeNode parent;

        ResourcesCallback(TreeNode parent) {
            this.parent = parent;
        }
        @Override
        public void onResponse(Call<List<Resource>> call, Response<List<Resource>> response) {

            if(!response.isSuccessful()) {
                Gson gson = new GsonBuilder().create();
                try {
                    Error mApiError = gson.fromJson(response.errorBody().string(), Error.class);
                    Log.d("Response on feed metric", mApiError.getErrorMsg());
                } catch (IOException e) {
                    // handle failure to read error
                }
            }

            else
            {
                //Log.d("Response","code="+ response.code());
                getInventoryExplorerActivity().setUpResources(response.body(), parent);
            }

            //Log.d("Response on feed metric", error.getErrorMsg());
            /*if(!response.body().isEmpty()) {
                getInventoryExplorerActivity().setUpResources(response.body(), parent);
            }
            else {
            }*/

        }

        @Override
        public void onFailure(Call<List<Resource>> call, Throwable t) {
            Timber.d("Resources fetching failed.");
            Log.d("Response on feed metric", t.getMessage());
        }
    }

    private final class MetricsCallback extends AbstractActivityCallback<List<Metric>> {

        private TreeNode parent;

        MetricsCallback(TreeNode parent) {
            this.parent = parent;
        }

        @Override
        public void onSuccess(List<Metric> metrics) {
            if (!metrics.isEmpty()) {
                getInventoryExplorerActivity().setUpMetrics(metrics, parent);
            }
        }

        @Override
        public void onFailure(Exception e) {
            Timber.d("Resources fetching failed.");

        }

        private InventoryExplorerActivity getInventoryExplorerActivity() {
            return (InventoryExplorerActivity) getActivity();
        }
    }

    private final class PerformOperationCallback implements Callback<String> {

        @Override public void onSuccess(String data) {
            Snackbar.make(findViewById(android.R.id.content), data, Snackbar.LENGTH_LONG).show();

        }

        @Override public void onFailure(Exception e) {
            Snackbar.make(findViewById(android.R.id.content), R.string.operation_fail, Snackbar.LENGTH_LONG).show();
        }
    }
}
