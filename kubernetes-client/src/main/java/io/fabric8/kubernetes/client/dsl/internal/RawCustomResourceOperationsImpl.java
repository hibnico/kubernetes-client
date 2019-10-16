/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.dsl.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import io.fabric8.kubernetes.client.utils.IOHelpers;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.kubernetes.client.utils.WatcherToggle;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class simple does basic operations for custom defined resources without
 * demanding the POJOs for custom resources. It is serializing/deserializing
 * objects to plain hash map(String, Object).
 *
 * Right now it supports basic operations like GET, POST, PUT, DELETE.
 *
 */
public class RawCustomResourceOperationsImpl extends OperationSupport {
  private OkHttpClient client;
  private Config config;
  private CustomResourceDefinitionContext customResourceDefinition;
  private ObjectMapper objectMapper;

  private enum HttpCallMethod { GET, POST, PUT, DELETE };

  public RawCustomResourceOperationsImpl(OkHttpClient client, Config config, CustomResourceDefinitionContext customResourceDefinition) {
    this.client = client;
    this.config = config;
    this.customResourceDefinition = customResourceDefinition;
    this.objectMapper = Serialization.jsonMapper();
  }

  /**
   * Load a custom resource object from an inputstream into a HashMap
   *
   * @param fileInputStream file input stream
   * @return custom resource as HashMap
   * @throws IOException exception in case any read operation fails.
   */
  public Map<String, Object> load(InputStream fileInputStream) throws IOException {
    return convertJsonStringToMap(IOHelpers.readFully(fileInputStream));
  }

  /**
   * Load a custom resource object from a JSON string into a HashMap
   *
   * @param objectAsJsonString object as JSON string
   * @return custom resource as HashMap
   * @throws IOException exception in case any problem in reading json.
   */
  public Map<String, Object> load(String objectAsJsonString) throws IOException {
    return convertJsonStringToMap(objectAsJsonString);
  }

  /**
   * Create a custom resource which is a non-namespaced object.
   *
   * @param objectAsString object as JSON string
   * @return Object as HashMap
   * @throws IOException exception in case of any network/read problems
   */
  public Map<String, Object> create(String objectAsString) throws IOException {
    return validateAndSubmitRequest(null, null, objectAsString, HttpCallMethod.POST);
  }

  /**
   * Create a custom resource which is non-namespaced.
   *
   * @param object object a HashMap
   * @return Object as HashMap
   * @throws KubernetesClientException in case of error from Kubernetes API
   * @throws IOException in case of problems while reading HashMap
   */
  public Map<String, Object> create(Map<String, Object> object) throws KubernetesClientException, IOException {
    return validateAndSubmitRequest(null, null, objectMapper.writeValueAsString(object), HttpCallMethod.POST);
  }

  /**
   * Create a custom resource which is a namespaced object.
   *
   * @param namespace namespace in which we want object to be created.
   * @param objectAsString Object as JSON string
   * @return Object as HashMap
   * @throws KubernetesClientException in case of error from Kubernetes API
   * @throws IOException in case of problems while reading JSON object
   */
  public Map<String, Object> create(String namespace, String objectAsString) throws KubernetesClientException, IOException {
    return validateAndSubmitRequest(namespace, null, objectAsString, HttpCallMethod.POST);
  }

  /**
   * Create a custom resource which is non-namespaced object.
   *
   * @param objectAsStream object as a file input stream
   * @return Object as HashMap
   * @throws KubernetesClientException in case of error from Kubernetes API
   * @throws IOException in case of problems while reading file
   */
  public Map<String, Object> create(InputStream objectAsStream) throws KubernetesClientException, IOException {
    return validateAndSubmitRequest(null, null, IOHelpers.readFully(objectAsStream), HttpCallMethod.POST);
  }

  /**
   * Create a custom resource which is a namespaced object.
   *
   * @param namespace namespace in which we want object to be created
   * @param objectAsStream object as file input stream
   * @return Object as HashMap
   * @throws KubernetesClientException in case of error from Kubernetes API
   * @throws IOException in case of problems while reading file
   */
  public Map<String, Object> create(String namespace, InputStream objectAsStream) throws KubernetesClientException, IOException {
    return validateAndSubmitRequest(namespace, null, IOHelpers.readFully(objectAsStream), HttpCallMethod.POST);
  }

  /**
   * Create a custom resource which is a namespaced object.
   *
   * @param namespace namespace in which we want object to be created.
   * @param object object as a HashMap
   * @return Object as HashMap
   * @throws KubernetesClientException in case of error from Kubernetes API
   * @throws IOException in case of problems faced while serializing HashMap
   */
  public Map<String, Object> create(String namespace, Map<String, Object> object) throws KubernetesClientException, IOException {
    return validateAndSubmitRequest(namespace, null, objectMapper.writeValueAsString(object), HttpCallMethod.POST);
  }

  /**
   *
   * Create or replace a custom resource which is a non-namespaced object.
   *
   * @param objectAsString object as JSON string
   * @return Object as HashMap
   * @throws IOException in case of network/serializiation failures or failures from Kuberntes API
   */
  public Map<String, Object> createOrReplace(String objectAsString) throws IOException {
    return createOrReplaceJsonStringObject(null, objectAsString);
  }

  /**
   * Create or replace a custom resource which is a non-namespced object.
   *
   * @param customResourceObject object as HashMap
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> createOrReplace(Map<String, Object> customResourceObject) throws IOException {
    return createOrReplace(objectMapper.writeValueAsString(customResourceObject));
  }

  /**
   * Create or replace a custom resource which is non-namespaced object.
   *
   * @param inputStream object as file input stream
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> createOrReplace(InputStream inputStream) throws IOException {
    return createOrReplace(IOHelpers.readFully(inputStream));
  }

  /**
   * Create or replace a custom resource which is namespaced object.
   *
   * @param namespace desired namespace
   * @param objectAsString object as JSON String
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> createOrReplace(String namespace, String objectAsString) throws IOException {
    return createOrReplaceJsonStringObject(namespace, objectAsString);
  }

  /**
   * Create or replace a custom resource which is namespaced object.
   *
   * @param namespace desired namespace
   * @param customResourceObject object as HashMap
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> createOrReplace(String namespace, Map<String, Object> customResourceObject) throws IOException {
    return createOrReplace(namespace, objectMapper.writeValueAsString(customResourceObject));
  }

  /**
   * Create or replace a custom resource which is namespaced object.
   *
   * @param namespace desired namespace
   * @param objectAsString object as file input stream
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> createOrReplace(String namespace, InputStream objectAsString) throws IOException {
    return createOrReplace(namespace, IOHelpers.readFully(objectAsString));
  }

  /**
   * Edit a custom resource object which is a non-namespaced object.
   *
   * @param name name of the custom resource
   * @param object new object as a HashMap
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String name, Map<String, Object> object) throws IOException {
    return validateAndSubmitRequest(null, name, objectMapper.writeValueAsString(object), HttpCallMethod.PUT);
  }

  /**
   * Edit a custom resource object which is a non-namespaced object.
   *
   * @param name name of the custom resource
   * @param objectAsString new object as a JSON String
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String name, String objectAsString) throws IOException {
    return validateAndSubmitRequest(null, name, objectAsString, HttpCallMethod.PUT);
  }

  /**
   * Edit a custom resource object which is a namespaced object.
   *
   * @param namespace desired namespace
   * @param name name of the custom resource
   * @param object new object as a HashMap
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String namespace, String name, Map<String, Object> object) throws IOException {
    return validateAndSubmitRequest(namespace, name, objectMapper.writeValueAsString(object), HttpCallMethod.PUT);
  }

  /**
   * Edit a custom resource object which is a namespaced object.
   *
   * @param namespace desired namespace
   * @param name name of the custom resource
   * @param objectAsString new object as a JSON string
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String namespace, String name, String objectAsString) throws IOException {
    // Append resourceVersion in object metadata in order to
    // avoid : https://github.com/fabric8io/kubernetes-client/issues/1724
    objectAsString = appendResourceVersionInObject(namespace, name, objectAsString);
    return validateAndSubmitRequest(namespace, name, objectAsString, HttpCallMethod.PUT);
  }

  /**
   * Edit a custom resource object which is a non-namespaced object.
   *
   * @param name name of the custom resource
   * @param objectAsStream new object as a file input stream
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String name, InputStream objectAsStream) throws IOException, KubernetesClientException {
    return validateAndSubmitRequest(null, name, IOHelpers.readFully(objectAsStream), HttpCallMethod.PUT);
  }

  /**
   * Edit a custom resource object which is a namespaced object.
   *
   * @param namespace desired namespace
   * @param name name of the custom resource
   * @param objectAsStream new object as a file input stream
   * @return Object as HashMap
   * @throws IOException in case of network/serialization failures or failures from Kubernetes API
   */
  public Map<String, Object> edit(String namespace, String name, InputStream objectAsStream) throws IOException, KubernetesClientException {
    return validateAndSubmitRequest(namespace, name, IOHelpers.readFully(objectAsStream), HttpCallMethod.PUT);
  }

  /**
   * Get a custom resource from the cluster which is non-namespaced.
   *
   * @param name name of custom resource
   * @return Object as HashMap
   */
  public Map<String, Object> get(String name) {
    return makeCall(fetchUrl(null, null) + name, null, HttpCallMethod.GET);
  }

  /**
   * Get a custom resource from the cluster which is namespaced.
   *
   * @param namespace desired namespace
   * @param name name of custom resource
   * @return Object as HashMap
   */
  public Map<String, Object> get(String namespace, String name) {
      return makeCall(fetchUrl(namespace, null) + name, null, HttpCallMethod.GET);
  }

  /**
   * List all custom resources in all namespaces
   *
   * @return list of custom resources as HashMap
   */
  public Map<String, Object> list() {
    return makeCall(fetchUrl(null, null), null, HttpCallMethod.GET);
  }

  /**
   * List all custom resources in a specific namespace
   *
   * @param namespace desired namespace
   * @return list of custom resources as HashMap
   */
  public Map<String, Object> list(String namespace) {
    return makeCall(fetchUrl(namespace, null), null, HttpCallMethod.GET);
  }

  /**
   * List all custom resources in a specific namespace with some labels
   *
   * @param namespace desired namespace
   * @param labels labels as a HashMap
   * @return list of custom resources as HashMap
   */
  public Map<String, Object> list(String namespace, Map<String, String> labels) {
    return makeCall(fetchUrl(namespace, labels), null, HttpCallMethod.GET);
  }

  /**
   * Delete all custom resources in a specific namespace
   *
   * @param namespace desired namespace
   * @return deleted objects as HashMap
   */
  public Map<String, Object> delete(String namespace) {
    return makeCall(fetchUrl(namespace, null), null, HttpCallMethod.DELETE);
  }

  /**
   * Delete a custom resource in a specific namespace
   *
   * @param namespace desired namespace
   * @param name custom resource's name
   * @return object as HashMap
   */
  public Map<String, Object> delete(String namespace, String name) {
    return makeCall(fetchUrl(namespace, null) + name, null, HttpCallMethod.DELETE);
  }

  /**
   * Watch custom resources in a specific namespace. Here Watcher is provided
   * for string type only. User has to deserialize object itself.
   *
   * @param namespace namespace to watch
   * @param watcher watcher object which reports updates with object
   * @throws IOException in case of network error
   */
  public void watch(String namespace, Watcher<String> watcher) throws IOException {
    watch(namespace, null, null, null, watcher);
  }

  /**
   * Watch a custom resource in a specific namespace with some resourceVersion. Here
   * watcher is provided from string type only. User has to deserialize object itself.
   *
   * @param namespace namespace to watch
   * @param resourceVersion resource version since when to watch
   * @param watcher watcher object which reports updates
   * @throws IOException in case of network error
   */
  public void watch(String namespace, String resourceVersion, Watcher<String> watcher) throws IOException {
    watch(namespace, null, null, resourceVersion, watcher);
  }

  /**
   * Watchers custom resources across all namespaces. Here watcher is provided
   * for string type only. User has to deserialize object itself.
   *
   * @param watcher watcher object which reports events
   * @throws IOException in case of network error
   */
  public void watch(Watcher<String> watcher) throws IOException {
    watch(null, null, null, null, watcher);
  }

  /**
   * Watch custom resources in the parameters specified.
   *
   * Most of the parameters except watcher are optional, they would be
   * skipped if passed null. Here watcher is provided for string type
   * only. User has to deserialize the object itself.
   *
   * @param namespace namespace to watch (optional
   * @param name name of custom resource (optional)
   * @param labels HashMap containing labels (optional)
   * @param resourceVersion resource version since when to watch (optional)
   * @param watcher watcher object which reports events
   * @throws IOException in case of network error
   */
  public Watch watch(String namespace, String name, Map<String, String> labels, String resourceVersion, Watcher<String> watcher) throws IOException {
    URL url = new URL(fetchUrl(name, namespace, labels));
    HttpUrl.Builder httpUrlBuilder = HttpUrl.get(url).newBuilder();
    if (resourceVersion != null) {
      httpUrlBuilder.addQueryParameter("resourceVersion", resourceVersion);
    }

    httpUrlBuilder.addQueryParameter("watch", "true");
    String origin = url.getProtocol() + "://" + url.getHost();
    if (url.getPort() != -1) {
      origin += ":" + url.getPort();
    }

    Request request = new Request.Builder()
      .get()
      .url(httpUrlBuilder.build())
      .addHeader("Origin", origin)
      .build();

    OkHttpClient.Builder clonedClientBuilder = client.newBuilder();
      clonedClientBuilder.readTimeout(getConfig() != null ?
        getConfig().getWebsocketTimeout() : Config.DEFAULT_WEBSOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
      clonedClientBuilder.pingInterval(getConfig() != null ?
        getConfig().getWebsocketPingInterval() : Config.DEFAULT_WEBSOCKET_PING_INTERVAL, TimeUnit.MILLISECONDS);

    OkHttpClient clonedOkHttpClient = clonedClientBuilder.build();
    WatcherToggle<String> watcherToggle = new WatcherToggle<>(watcher, true);
    RawWatchConnectionManager watch = null;
    try {
      watch = new RawWatchConnectionManager(
        clonedOkHttpClient, request, resourceVersion, objectMapper, watcher,
        getConfig() != null ? getConfig().getWatchReconnectLimit() : -1,
        getConfig() != null ? getConfig().getWatchReconnectInterval() : 1000,
        5);
      watch.waitUntilReady();
      return watch;
    } catch (KubernetesClientException ke) {

      if (ke.getCode() != 200) {
        if(watch != null){
          //release the watch
          watch.close();
        }

        throw ke;
      }

      if(watch != null){
        //release the watch after disabling the watcher (to avoid premature call to onClose)
        watcherToggle.disable();
        watch.close();
      }

      // If the HTTP return code is 200, we retry the watch again using a persistent hanging
      // HTTP GET. This is meant to handle cases like kubectl local proxy which does not support
      // websockets. Issue: https://github.com/kubernetes/kubernetes/issues/25126
      return new RawWatchConnectionManager(
        clonedOkHttpClient, request, resourceVersion, objectMapper, watcher,
        getConfig() != null ? getConfig().getWatchReconnectLimit() : -1,
        getConfig() != null ? getConfig().getWatchReconnectInterval() : 1000,
        5);
    }

  }

  private Map<String, Object> createOrReplaceJsonStringObject(String namespace, String objectAsString) throws IOException {
    Map<String, Object> ret;
    try {
      if(namespace != null) {
        ret = create(namespace, objectAsString);
      } else {
        ret = create(objectAsString);
      }
    } catch (KubernetesClientException exception) {
      try {
        Map<String, Object> objectMap = load(objectAsString);
        String name = ((Map<String, Object>) objectMap.get("metadata")).get("name").toString();
        ret =  namespace != null ?
          edit(namespace, name, objectAsString) : edit(name, objectAsString);
      } catch (NullPointerException nullPointerException) {
        throw KubernetesClientException.launderThrowable(new IllegalStateException("Invalid json string provided."));
      }
    }
    return ret;
  }

  private Map<String, Object> convertJsonStringToMap(String objectAsString) throws IOException {
    HashMap<String, Object> retVal = null;
    if (IOHelpers.isJSONValid(objectAsString)) {
      retVal =  objectMapper.readValue(objectAsString, HashMap.class);
    } else {
      retVal = objectMapper.readValue(IOHelpers.convertYamlToJson(objectAsString), HashMap.class);
    }
    return retVal;
  }

  private String fetchUrl(String name, String namespace, Map<String, String> labels) {
    String url = fetchUrl(namespace, labels);
    if (name != null) {
      return url + name;
    } else {
      return url.substring(0, url.length() - 1);
    }
  }

  private String fetchUrl(String namespace, Map<String, String> labels) {
    if (config.getMasterUrl() == null) {
      return null;
    }

    StringBuilder urlBuilder = new StringBuilder(config.getMasterUrl());

    urlBuilder.append(config.getMasterUrl().endsWith("/") ? "" : "/");
    urlBuilder.append("apis/")
      .append(customResourceDefinition.getGroup())
      .append("/")
      .append(customResourceDefinition.getVersion())
      .append("/");

    if(customResourceDefinition.getScope().equals("Namespaced") && namespace != null) {
      urlBuilder.append("namespaces/").append(namespace).append("/");
    }
    urlBuilder.append(customResourceDefinition.getPlural()).append("/");
    if(labels != null) {
      urlBuilder.deleteCharAt(urlBuilder.lastIndexOf("/"));
      urlBuilder.append("?labelSelector").append("=").append(getLabelsQueryParam(labels));
    }
    return urlBuilder.toString();
  }

  private String getLabelsQueryParam(Map<String, String> labels) {
    StringBuilder labelQueryBuilder = new StringBuilder();
    for(Map.Entry<String, String> entry : labels.entrySet()) {
      if(labelQueryBuilder.length() > 0) {
        labelQueryBuilder.append(",");
      }
      labelQueryBuilder.append(entry.getKey()).append(Utils.toUrlEncoded("=")).append(entry.getValue());
    }
    return labelQueryBuilder.toString();
  }

  private Map<String, Object> makeCall(String url, String body, HttpCallMethod callMethod) {
    Request request = (body == null) ? getRequest(url, callMethod) : getRequest(url, body, callMethod);
    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return objectMapper.readValue(response.body().string(), HashMap.class);
      } else {
        String message = String.format("Error while performing the call to %s. Response code: %s", url, response.code());
        Status status = createStatus(response);
        throw new KubernetesClientException(message, response.code(), status);
      }
    } catch(Exception e) {
      throw KubernetesClientException.launderThrowable(e);
    }
  }

  private Map<String, Object> validateAndSubmitRequest(String namespace, String name, String objectAsString, HttpCallMethod httpCallMethod) throws IOException {
    if (IOHelpers.isJSONValid(objectAsString)) {
      return makeCall(fetchUrl(namespace, null) + (name != null ? name : ""), objectAsString, httpCallMethod);
    } else {
      return makeCall(fetchUrl(namespace, null) + (name != null ? name : ""), IOHelpers.convertYamlToJson(objectAsString), httpCallMethod);
    }
  }

  private Request getRequest(String url, HttpCallMethod httpCallMethod) {
    Request.Builder requestBuilder = new Request.Builder();
    switch(httpCallMethod) {
      case GET:
        requestBuilder.get().url(url);
        break;
      case DELETE:
        requestBuilder.delete().url(url);
        break;
    }

    return requestBuilder.build();
  }

  private Request getRequest(String url, String body, HttpCallMethod httpCallMethod) {
    Request.Builder requestBuilder = new Request.Builder();
    RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
    switch(httpCallMethod) {
      case POST:
        return requestBuilder.post(requestBody).url(url).build();
      case PUT:
        return requestBuilder.put(requestBody).url(url).build();
    }
    return requestBuilder.build();
  }

  private String appendResourceVersionInObject(String namespace, String customResourceName, String customResourceAsJsonString) throws IOException {
    Map<String, Object> oldObject = get(namespace, customResourceName);
    String resourceVersion = ((Map<String, Object>)oldObject.get("metadata")).get("resourceVersion").toString();

    Map<String, Object> newObject = convertJsonStringToMap(customResourceAsJsonString);
    ((Map<String, Object>)newObject.get("metadata")).put("resourceVersion", resourceVersion);

    return objectMapper.writeValueAsString(newObject);
  }
}