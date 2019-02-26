/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.extensions.callbacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.services.cluster.ClusterDiscoveryCallback;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryInput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryOutput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Florian Limpöck
 * @author Abdullah Imal
 * @author Alwin Ebermann
 * @since 0.1
 */
public class DcosDiscoveryCallback implements ClusterDiscoveryCallback {

    private static final Logger logger = LoggerFactory.getLogger(DcosDiscoveryCallback.class);

    @Override
    public void init(@NotNull final ClusterDiscoveryInput clusterDiscoveryInput, @NotNull final ClusterDiscoveryOutput clusterDiscoveryOutput) {
        clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
    }

    @Override
    public void reload(@NotNull final ClusterDiscoveryInput clusterDiscoveryInput, @NotNull final ClusterDiscoveryOutput clusterDiscoveryOutput) {
        clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
    }

    @Override
    public void destroy(@NotNull ClusterDiscoveryInput clusterDiscoveryInput) {

    }

    @NotNull
    private List<ClusterNodeAddress> getNodeAddresses() {
        final List<ClusterNodeAddress> nodeAddresses = new ArrayList<>();

        final String schedulerHostName = System.getenv("SCHEDULER_API_HOSTNAME");
        int schedulerPort;
        try {
            schedulerPort = Integer.parseInt(System.getenv("SCHEDULER_API_PORT"));
        } catch (NumberFormatException e) {
            schedulerPort = 0;
        }
        if (schedulerPort < 1 || schedulerHostName == null) {
            logger.error("SCHEDULER_API_HOSTNAME or SCHEDULER_API_PORT is invalid");
        } else {
            try {
                URL url = new URL("http://" + schedulerHostName + ":" + schedulerPort + "/v1/endpoints/tcp-discovery");
                URLConnection request = url.openConnection();
                request.connect();
                JsonParser jp = new JsonParser();
                JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
                JsonObject rootobj = root.getAsJsonObject();
                JsonArray discoveryEndpoints = rootobj.get("address").getAsJsonArray();
                for (JsonElement discoveryEndpoint : discoveryEndpoints) {
                    String endpoint = discoveryEndpoint.getAsString();
                    String[] parts = endpoint.split(":");
                    nodeAddresses.add(new ClusterNodeAddress(parts[0], Integer.parseInt(parts[1])));
                }

            } catch (MalformedURLException e) {
                logger.error("DC/OS discovery: Error in DC/OS scheduler env "+e);
            } catch (IOException e) {
                logger.error("DC/OS discovery: Error processing DC/OS endpoints endpoint");
            }
        }
        if (nodeAddresses.size() == 0) {
            logger.error("DC/OS discovery: Did not get any endpoints from scheduler");
        }
        return nodeAddresses;
    }
}
