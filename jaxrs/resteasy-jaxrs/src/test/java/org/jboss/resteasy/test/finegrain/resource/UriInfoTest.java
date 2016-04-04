package org.jboss.resteasy.test.finegrain.resource;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.plugins.server.resourcefactory.SingletonResource;
import org.jboss.resteasy.test.EmbeddedContainer;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.jboss.resteasy.test.TestPortProvider.createURI;
import static org.jboss.resteasy.test.TestPortProvider.generateURL;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class UriInfoTest
{
   private static Dispatcher dispatcher;

   @BeforeClass
   public static void before() throws Exception
   {
   }

   @AfterClass
   public static void after() throws Exception
   {
   }

   @Path("/")
   public static class SimpleResource
   {
      @Path("/simple")
      @GET
      public String get(@Context UriInfo info, @QueryParam("abs") String abs)
      {
         System.out.println("abs query: " + abs);
         URI base = null;
         if (abs == null)
         {
            base = createURI("/");
         }
         else
         {
            base = createURI("/" + abs + "/");
         }

         System.out.println("BASE URI: " + info.getBaseUri());
         System.out.println("Request URI: " + info.getRequestUri());
         Assert.assertEquals(base.getPath(), info.getBaseUri().getPath());
         Assert.assertEquals("/simple", info.getPath());
         return "CONTENT";
      }

      @Context
      UriInfo myInfo;

      @Path("/simple/fromField")
      @GET
      public String get(@QueryParam("abs") String abs)
      {
         System.out.println("abs query: " + abs);
         URI base = null;
         if (abs == null)
         {
            base = createURI("/");
         }
         else
         {
            base = createURI("/" + abs + "/");
         }

         System.out.println("BASE URI: " + myInfo.getBaseUri());
         System.out.println("Request URI: " + myInfo.getRequestUri());
         Assert.assertEquals(base.getPath(), myInfo.getBaseUri().getPath());
         Assert.assertEquals("/simple/fromField", myInfo.getPath());
         return "CONTENT";
      }

   }

   private void _test(String path)
   {
      ClientRequest request = new ClientRequest(generateURL(path));
      try
      {
         ClientResponse<?> response = request.get();
         Assert.assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
         response.releaseConnection();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   @Test
   public void testUriInfoWithSingleton() throws Exception
   {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addResourceFactory(new SingletonResource(new SimpleResource()));
         _test("/simple/fromField");
      }
      finally
      {
         EmbeddedContainer.stop();
      }

   }

   @Test
   public void testUriInfo() throws Exception
   {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(SimpleResource.class);
         _test("/simple");
         _test("/simple/fromField");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   @Test
   public void testUriInfo2() throws Exception
   {
      dispatcher = EmbeddedContainer.start("/resteasy").getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(SimpleResource.class);
         _test("/resteasy/simple?abs=resteasy");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   @Path("/{a}/{b}")
   public static class EncodedTemplateResource
   {
      @GET
      public String doGet(@PathParam("a") String a, @PathParam("b") String b, @Context UriInfo info)
      {
         Assert.assertEquals("a b", a);
         Assert.assertEquals("x y", b);
         Assert.assertEquals("a b", info.getPathParameters().getFirst("a"));
         Assert.assertEquals("x y", info.getPathParameters().getFirst("b"));
         Assert.assertEquals("a%20b", info.getPathParameters(false).getFirst("a"));
         Assert.assertEquals("x%20y", info.getPathParameters(false).getFirst("b"));

         List<PathSegment> decoded = info.getPathSegments(true);
         Assert.assertEquals(decoded.size(), 2);
         Assert.assertEquals("a b", decoded.get(0).getPath());
         Assert.assertEquals("x y", decoded.get(1).getPath());

         List<PathSegment> encoded = info.getPathSegments(false);
         Assert.assertEquals(encoded.size(), 2);
         Assert.assertEquals("a%20b", encoded.get(0).getPath());
         Assert.assertEquals("x%20y", encoded.get(1).getPath());
         return "content";
      }
   }

   @Path("/queryEscapedMatrParam")
   public static class EscapedMatrParamResource
   {
      @GET
      public String doGet(@MatrixParam("a") String a, @MatrixParam("b") String b, @MatrixParam("c") String c, @MatrixParam("d") String d)
      {
         Assert.assertEquals("a;b", a);
         Assert.assertEquals("x/y", b);
         Assert.assertEquals("m\\n", c);
         Assert.assertEquals("k=l", d);
         return "content";
      }
   }

   @Test
   public void testEscapedMatrParam() throws Exception
   {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(EscapedMatrParamResource.class);
         _test("/queryEscapedMatrParam;a=a%3Bb;b=x%2Fy;c=m%5Cn;d=k%3Dl");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   @Test
   public void testEncodedTemplateParams() throws Exception
   {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(EncodedTemplateResource.class);
         _test("/a%20b/x%20y");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   @Path("/query")
   public static class EncodedQueryResource
   {
      @GET
      public String doGet(@QueryParam("a") String a, @Context UriInfo info)
      {
         Assert.assertEquals("a b", a);
         Assert.assertEquals("a b", info.getQueryParameters().getFirst("a"));
         Assert.assertEquals("a%20b", info.getQueryParameters(false).getFirst("a"));
         return "content";
      }
   }

   @Test
   public void testEncodedQueryParams() throws Exception
   {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(EncodedQueryResource.class);
         _test("/query?a=a%20b");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   @Path("/queryParams")
   public static class QueryParamsResource
   {
      @GET
      public String doGet(@QueryParam("a") String a, @Context UriInfo info)
      {
         Assert.assertNotNull(info);

         Assert.assertNotNull(info.getQueryParameters());
         assertNotMutable(info.getQueryParameters());

         return "content";
      }
   }

   @Test
   public void testQueryParamsMutability() throws Exception {
      dispatcher = EmbeddedContainer.start().getDispatcher();
      try
      {
         dispatcher.getRegistry().addPerRequestResource(QueryParamsResource.class);
         _test("/queryParams?a=a,b");
      }
      finally
      {
         EmbeddedContainer.stop();
      }
   }

   private static void assertNotMutable(MultivaluedMap<String, String> params) {

      final String param = "param";
      final String key = params.keySet().iterator().next();

      try {
         params.put(param, Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.get(key).add(param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.add(param, param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.addAll(param, Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.addAll(param, param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.addFirst(param, param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.putSingle(param, param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.entrySet().add(new Map.Entry<String, List<String>>() {
            @Override
            public String getKey() {
               return param;
            }

            @Override
            public List<String> getValue() {
               return Collections.singletonList(param);
            }

            @Override
            public List<String> setValue(List<String> value) {
               return Collections.singletonList(param);
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.keySet().add(param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.clear();
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.putAll(params);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.remove(key);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.remove(null);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.values().add(Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.compute(key, new BiFunction<String, List<String>, List<String>>() {
            @Override
            public List<String> apply(String key, List<String> value) {
               return Collections.emptyList();
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.computeIfAbsent(param, new Function<String, List<String>>() {
            @Override
            public List<String> apply(String s) {
               return Collections.emptyList();
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.computeIfPresent(key, new BiFunction<String, List<String>, List<String>>() {
            @Override
            public List<String> apply(String s, List<String> strings) {
               return Collections.emptyList();
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.merge(key, Collections.singletonList(param), new BiFunction<List<String>, List<String>, List<String>>() {
            @Override
            public List<String> apply(List<String> strings, List<String> strings2) {
               return Collections.emptyList();
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.getOrDefault(key, null).add(param);
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.putIfAbsent(param, Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.replace(key, Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.replace(key, new ArrayList<String>(), Collections.singletonList(param));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.replaceAll(new BiFunction<String, List<String>, List<String>>() {
            @Override
            public List<String> apply(String s, List<String> strings) {
               return Collections.emptyList();
            }
         });
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }

      try {
         params.remove(key, params.getFirst("a"));
         Assert.fail("mutable UriInfo");
      } catch (UnsupportedOperationException uoe) {
         //OK
      }
   }
}
