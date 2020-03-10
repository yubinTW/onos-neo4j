/*
 * Copyright 2019-present Open Networking Foundation
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
package yubincom;

import com.google.common.collect.ImmutableSet;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import static org.neo4j.driver.v1.Values.parameters;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onosproject.net.host.HostService;
import org.onosproject.net.Host;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.Device;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Link;

import java.util.Dictionary;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = { SomeInterface.class }, property = {
        "someProperty=Some Default String Value", })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;
    private String uri = "bolt://127.0.0.1:7687";
    private String username = "neo4j";
    private String password = "password";

    private Driver driver;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        log.info("Started yo yo ~ Yubin App");
        driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        clearDatabase();
        printGreeting("Hello Neo4j!");

        for (Device device : deviceService.getDevices()) {
            addDeviceToDatabase(device);
        }

        for (Link link : linkService.getLinks()) {
            addLinkToDatabase(link);
        }
        
        for (Host host : hostService.getHosts()) {
            addHostToDatabase(host);
        }
        
        
        log.info("End of activate()");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    public void clearDatabase() {
        try (Session session = driver.session()) {
            // delete all nodes
            session.run("MATCH (n) DETACH DELETE n");
            log.info("delete all nodes and relationships");
        }
    }

    public void addHostToDatabase(Host host) {
        String id = host.id().toString();
        String macAddress = host.mac().toString();
        String vlanId = host.vlan().toString();
        String location = host.location().deviceId().toString();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", id);
        parameters.put("location", location);
        // parameters.put("mac", macAddress);
        // parameters.put("vlanid", vlanId);
        try (Session session = driver.session()) {
            session.run("MERGE (a:Host{id:$id}) RETURN a", parameters);
            session.run("MATCH (a:Host{id:$id}) MATCH (b:Device{id:$location}) MERGE (a)-[c:LINK{type:'EDGE'}]-(b) ", parameters);
            log.info("add host to database: " + parameters.toString());
        }

    }

    public void addDeviceToDatabase(Device device) {
        String id = device.id().toString();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", id);
        try (Session session = driver.session()) {
            session.run("MERGE (a:Device{id:$id}) RETURN a", parameters);
            log.info("add device to database: " + parameters.toString());
        }
    }


    public void addLinkToDatabase(Link link) {
        String src = link.src().deviceId().toString();
        String dst = link.dst().deviceId().toString();
        String type = link.type().name();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src", src);
        parameters.put("dst", dst);
        parameters.put("type", type);
        try (Session session = driver.session()) {
            session.run("MATCH (a{id:$src}) MATCH (b{id:$dst}) MERGE (a)-[c:LINK{type:$type}]-(b) RETURN c", parameters);
            log.info("add link: " + parameters.toString());
        }
    }


    public void printGreeting(final String message) {
        // driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password) );
        try (Session session = driver.session()) {
            String greeting = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    StatementResult result = tx.run(
                            "CREATE (a:Greeting) SET a.message = $message RETURN a.message + ', from node ' + id(a)",
                            parameters("message", message));
                    return result.single().get(0).asString();
                }
            });
            System.out.println(greeting);
            log.info(greeting);
        }
    }

}
