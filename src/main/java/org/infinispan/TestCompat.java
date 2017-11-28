package org.infinispan;

import static org.apache.http.auth.AuthScope.ANY;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.apache.http.HttpResponse;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;

import net.spy.memcached.CachedData;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.Transcoder;

public class TestCompat {

   private static final String SERVER_HOST = "localhost";
   private static final String CACHE_NAME = "compat";

   private static final int MEMCACHED_PORT = 11211;
   private static final int MEMCACHED_TIMEOUT = 60000;
   private static final int MEMCACHED_EXPIRATION = 3600;
   private static final Transcoder<Object> MEMCACHED_TRANSCODER = new MemCachedTranscoder();

   private static final String REST_URL = "http://localhost:8080/rest/" + CACHE_NAME;

   private static final JavaSerializationMarshaller JAVA_MARSHALLER = new JavaSerializationMarshaller();

   private static MemcachedClient createMemcachedClient() throws IOException {
      DefaultConnectionFactory d = new DefaultConnectionFactory() {
         @Override
         public long getOperationTimeout() {
            return MEMCACHED_TIMEOUT;
         }
      };
      return new MemcachedClient(d, Collections.singletonList(new InetSocketAddress(SERVER_HOST, MEMCACHED_PORT)));
   }

   private static CloseableHttpClient createRestClient() {
      CredentialsProvider provider = new BasicCredentialsProvider();
      Credentials credentials = new UsernamePasswordCredentials("user", "user");
      provider.setCredentials(ANY, credentials);
      return HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
   }

   private static RemoteCache<String, byte[]> createHotRodClient() {
      RemoteCacheManager remoteCacheManager = new RemoteCacheManager();
      return remoteCacheManager.getCache(CACHE_NAME);
   }

   private static boolean areArraysEquals(byte[] array, byte[] another) {
      if (array == null) return another == null;
      if (another == null) return false;
      if (array.length != another.length) throw new AssertionError("Arrays have different sizes");
      for (int i = 0; i < array.length; i++) {
         if (array[i] != another[i]) return false;
      }
      return true;
   }

   private static byte[] getWithRest(CloseableHttpClient restClient, String key) throws IOException {
      HttpGet get = new HttpGet(REST_URL + "/" + key);

      HttpResponse getResponse = restClient.execute(get);
      verify(200 == getResponse.getStatusLine().getStatusCode());
      return EntityUtils.toByteArray(getResponse.getEntity());
   }

   private static void putWithRest(CloseableHttpClient restClient, String key, byte[] value) throws IOException {
      HttpPut put = new HttpPut(REST_URL + "/" + key);
      put.setEntity(new ByteArrayEntity(value, ContentType.APPLICATION_OCTET_STREAM));
      HttpResponse putResponse = restClient.execute(put);
      verify(200 == putResponse.getStatusLine().getStatusCode());
   }

   private static byte[] getWithMemCached(MemcachedClient memcachedClient, String key) throws IOException, ClassNotFoundException {
      return (byte[])memcachedClient.get(key, MEMCACHED_TRANSCODER);
   }

   private static void putWithMemCached(MemcachedClient memcachedClient, String key, byte[] value) throws IOException, ClassNotFoundException {
      memcachedClient.set(key, MEMCACHED_EXPIRATION, value, MEMCACHED_TRANSCODER);
   }

   private static byte[] getWithHotRodClient(RemoteCache<String,byte[]> hotRodClient, String key) throws IOException, ClassNotFoundException {
      return hotRodClient.get(key);
   }

   private static void verify(boolean condition) {
      if (!condition) {
         throw new RuntimeException("ERROR verifying condition");
      }

   }

   public static void main(String[] args) throws IOException, ClassNotFoundException {
      RemoteCache<String, byte[]> hotRodClient = createHotRodClient();
      CloseableHttpClient httpClient = createRestClient();
      MemcachedClient memcachedClient = createMemcachedClient();

      String key1 = "KEY1";
      byte[] value1 = new byte[]{1, 2, 3};

      String key2 = "KEY2";
      byte[] value2 = new byte[]{4, 5, 6};

      String key3 = "KEY3";
      byte[] value3 = new byte[]{7, 8, 9};

      /*
       * Writing via Hot Rod first, and reading from Rest and Memcached
       */
      hotRodClient.put(key1, value1);
      verify(areArraysEquals(hotRodClient.get(key1), value1));

      // Read with Rest
      byte[] valueFromRest = getWithRest(httpClient, key1);
      verify(areArraysEquals(valueFromRest, value1));

      // Read with Memcached
      byte[] fromMemcached = getWithMemCached(memcachedClient, key1);
      verify(areArraysEquals(fromMemcached, value1));



      /*
       * Writing via REST first, reading via HR and memcached
       */
      putWithRest(httpClient, key2, value2);
      byte[] fromRest = getWithRest(httpClient, key2);
      verify(areArraysEquals(fromRest, value2));

      // Read with Hot Rod
      byte[] fromHotRod = getWithHotRodClient(hotRodClient, key2);
      verify(areArraysEquals(fromHotRod, value2));

      // Read with Memcached
      fromMemcached = getWithMemCached(memcachedClient, key2);
      verify(areArraysEquals(fromMemcached, value2));


      /*
       * Writing via Memcached first, reading via HR and rest
       */
      putWithMemCached(memcachedClient, key3, value3);

      // Read with Hot Rod
      fromHotRod = getWithHotRodClient(hotRodClient, key3);
      verify(areArraysEquals(fromHotRod, value3));

      // Read with Rest
      valueFromRest = getWithRest(httpClient, key3);
      verify(areArraysEquals(valueFromRest, value3));

      memcachedClient.shutdown();
      hotRodClient.getRemoteCacheManager().stop();
      httpClient.close();

      System.out.println("SUCCESS");

   }


   /**
    * Transcoder to be used with the Spymemcached client, that uses java serialization.
    */
   static class MemCachedTranscoder implements Transcoder<Object> {

      @Override
      public boolean asyncDecode(CachedData d) {
         return false;
      }

      @Override
      public CachedData encode(Object o) {
         try {
            return new CachedData(0, JAVA_MARSHALLER.objectToByteBuffer(o), getMaxSize());
         } catch (IOException | InterruptedException e) {
            e.printStackTrace();
         }
         return null;
      }

      @Override
      public Object decode(CachedData d) {
         try {
            return JAVA_MARSHALLER.objectFromByteBuffer(d.getData());
         } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
         }
         return null;
      }

      @Override
      public int getMaxSize() {
         return 1024 * 1024;
      }
   }
}
