/*
    Copyright 2010, Strategic Gains, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/
package com.strategicgains.restexpress.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import com.strategicgains.restexpress.ContentType;
import com.strategicgains.restexpress.Format;
import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.Response;
import com.strategicgains.restexpress.exception.BadRequestException;
import com.strategicgains.restexpress.response.DefaultResponseWrapper;
import com.strategicgains.restexpress.response.RawResponseWrapper;
import com.strategicgains.restexpress.response.ResponseProcessor;
import com.strategicgains.restexpress.response.ResponseProcessorResolver;
import com.strategicgains.restexpress.response.StringBufferHttpResponseWriter;
import com.strategicgains.restexpress.route.RouteDeclaration;
import com.strategicgains.restexpress.route.RouteResolver;
import com.strategicgains.restexpress.serialization.AliasingSerializationProcessor;
import com.strategicgains.restexpress.settings.RouteDefaults;


/**
 * @author toddf
 * @since Dec 15, 2010
 */
public class DefaultRequestHandlerTest
{
	private DefaultRequestHandler messageHandler;
	private DummyObserver observer;
	private Channel channel;
    private ChannelPipeline pl;
    private StringBuffer responseBody;
    private Map<String, List<String>> responseHeaders;
	
	@Before
	public void initialize()
	throws Exception
	{
		ResponseProcessorResolver resolver = new ResponseProcessorResolver();
		resolver.put(Format.WRAPPED_JSON, ResponseProcessor.newJsonProcessor(new DefaultResponseWrapper()));
		resolver.put(Format.JSON, ResponseProcessor.newJsonProcessor(new RawResponseWrapper()));
		ResponseProcessor xmlProcessor = ResponseProcessor.newXmlProcessor(new DefaultResponseWrapper());
		AliasingSerializationProcessor xmlSerializer = (AliasingSerializationProcessor) xmlProcessor.getSerializer();
		xmlSerializer.alias("dated", Dated.class);
		resolver.put(Format.XML, xmlProcessor);
		resolver.setDefaultFormat(Format.WRAPPED_JSON);
		
		DummyRoutes routes = new DummyRoutes();
		routes.defineRoutes();
		messageHandler = new DefaultRequestHandler(new RouteResolver(routes.createRouteMapping(new RouteDefaults())), resolver);
		observer = new DummyObserver();
		messageHandler.addMessageObserver(observer);
		responseBody = new StringBuffer();
		responseHeaders = new HashMap<String, List<String>>();
		messageHandler.setResponseWriter(new StringBufferHttpResponseWriter(responseHeaders, responseBody));
		PipelineBuilder pf = new PipelineBuilder()
			.addRequestHandler(messageHandler);
	    pl = pf.getPipeline();
	    ChannelFactory channelFactory = new DefaultLocalServerChannelFactory();
	    channel = channelFactory.newChannel(pl);
	}

	@Test
	public void shouldReturnTextPlainContentTypeByDefault()
	throws Exception
	{
		sendGetEvent("/unserializedDefault");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("should be text plain, here", responseBody.toString());
		assertTrue(responseHeaders.containsKey("Content-Type"));
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals("text/plain; charset=UTF-8", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfContentType()
	throws Exception
	{
		sendGetEvent("/unserialized");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Some kinda wonderful!</body></html>", responseBody.toString());
		assertTrue(responseHeaders.containsKey("Content-Type"));
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals("text/html", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfContentTypeViaHeader()
	throws Exception
	{
		sendGetEvent("/unserializedToo");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Wow! What a fabulous HTML body...</body></html>", responseBody.toString());
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals("text/html", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfArbitraryBody()
	throws Exception
	{
		sendGetEvent("/setBodyAction.html");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Arbitrarily set HTML body...</body></html>", responseBody.toString());
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals(ContentType.HTML, contentTypes.get(0));
	}

	@Test
	public void shouldNotifyObserverOnSuccess()
	throws Exception
	{
		sendGetEvent("/foo");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\"}", responseBody.toString());
	}

	@Test
	public void shouldNotifyObserverOnError()
	throws Exception
	{
		sendGetEvent("/bar");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleNonDecodableValueInQueryString()
	throws Exception
	{
		sendGetEvent("/bar?value=%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleUrlDecodeErrorInFormat()
	throws Exception
	{
		sendGetEvent("/foo.%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
//		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\",\"data\":\"BadRequestException\"}", responseBody.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"Requested representation format not supported: %target. Supported formats: json, wjson, xml\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldShouldThrowExceptionForErrorInFormat()
	throws Exception
	{
		sendGetEvent("/foo.unsupported");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"Requested representation format not supported: unsupported. Supported formats: json, wjson, xml\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleInvalidDecodeInQueryString()
	throws Exception
	{
		sendGetEvent("/foo?value=%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleUrlDecodeErrorInUrl()
	throws Exception
	{
		sendGetEvent("/%bar");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":404,\"status\":\"error\",\"message\":\"Unresolvable URL: http://null/%bar\",\"data\":\"NotFoundException\"}", responseBody.toString());
	}

	@Test
	public void shouldParseTimepointJson()
	{
		sendGetEvent("/date.wjson", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", responseBody.toString());
	}

	@Test
	public void shouldParseTimepointJsonUsingQueryString()
	{
		sendGetEvent("/date?format=wjson", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", responseBody.toString());
	}

	@Test
	public void shouldParseTimepointXml()
	{
		sendGetEvent("/date.xml", "<com.strategicgains.restexpress.pipeline.Dated><at>2010-12-17T120000Z</at></com.strategicgains.restexpress.pipeline.Dated>");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(responseBody.toString().startsWith("<response>"));
		assertTrue(responseBody.toString().contains("<code>200</code>"));
		assertTrue(responseBody.toString().contains("<data class=\"dated\">"));
		assertTrue(responseBody.toString().contains("<at>2010-12-17T12:00:00.000Z</at>"));
		assertTrue(responseBody.toString().contains("</data>"));
		assertTrue(responseBody.toString().endsWith("</response>"));
	}

	@Test
	public void shouldParseTimepointXmlUsingQueryString()
	{
		sendGetEvent("/date?format=xml", "<dated><at>2010-12-17T120000Z</at></dated>");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(responseBody.toString().startsWith("<response>"));
		assertTrue(responseBody.toString().contains("<code>200</code>"));
		assertTrue(responseBody.toString().contains("<data class=\"dated\">"));
		assertTrue(responseBody.toString().contains("<at>2010-12-17T12:00:00.000Z</at>"));
		assertTrue(responseBody.toString().contains("</data>"));
		assertTrue(responseBody.toString().endsWith("</response>"));
	}

	@Test
	public void shouldThrowExceptionOnInvalidUrl()
	{
		sendGetEvent("/xyzt.xml");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(0, observer.getSuccessCount());
		assertEquals(1, observer.getExceptionCount());
//		System.out.println(responseBody.toString());
		assertTrue(responseBody.toString().startsWith("<response>"));
		assertTrue(responseBody.toString().contains("<code>404</code>"));
		assertTrue(responseBody.toString().contains("<status>error</status>"));
		assertTrue(responseBody.toString().contains("<message>Unresolvable URL: http://null/xyzt.xml</message>"));
		assertTrue(responseBody.toString().endsWith("</response>"));
	}

	@Test
	public void shouldThrowExceptionOnInvalidUrlWithXmlFormatQueryString()
	{
		sendGetEvent("/xyzt?format=xml");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(0, observer.getSuccessCount());
		assertEquals(1, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(responseBody.toString().startsWith("<response>"));
		assertTrue(responseBody.toString().contains("<code>404</code>"));
		assertTrue(responseBody.toString().contains("<status>error</status>"));
		assertTrue(responseBody.toString().contains("<message>Unresolvable URL: http://null/xyzt?format=xml</message>"));
		assertTrue(responseBody.toString().endsWith("</response>"));
	}

	@Test
	public void shouldCallAllFinallyProcessors()
	{
		NoopPostprocessor p1 = new NoopPostprocessor();
		NoopPostprocessor p2 = new NoopPostprocessor();
		NoopPostprocessor p3 = new NoopPostprocessor();
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/foo");
		assertEquals(1, p1.getCallCount());
		assertEquals(1, p2.getCallCount());
		assertEquals(1, p3.getCallCount());
	}

	@Test
	public void shouldCallAllFinallyProcessorsOnRouteException()
	{
		NoopPostprocessor p1 = new NoopPostprocessor();
		NoopPostprocessor p2 = new NoopPostprocessor();
		NoopPostprocessor p3 = new NoopPostprocessor();
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/xyzt.html");
		assertEquals(1, p1.getCallCount());
		assertEquals(1, p2.getCallCount());
		assertEquals(1, p3.getCallCount());
	}

	@Test
	public void shouldCallAllFinallyProcessorsOnProcessorException()
	{
		NoopPostprocessor p1 = new ExceptionPostprocessor();
		NoopPostprocessor p2 = new ExceptionPostprocessor();
		NoopPostprocessor p3 = new ExceptionPostprocessor();
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/foo");
		assertEquals(1, p1.getCallCount());
		assertEquals(1, p2.getCallCount());
		assertEquals(1, p3.getCallCount());
	}

	@Test
	public void shouldSetJSONContentType()
	throws Exception
	{
		sendGetEvent("/serializedString.json?returnValue=raw string");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals("\"raw string\"", responseBody.toString());
		assertTrue(responseHeaders.containsKey("Content-Type"));
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals("application/json; charset=UTF-8", contentTypes.get(0));
	}

	@Test
	public void shouldSetJSONContentTypeOnNullReturn()
	throws Exception
	{
		sendGetEvent("/serializedString.json");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertTrue(responseHeaders.containsKey("Content-Type"));
		List<String> contentTypes = responseHeaders.get(HttpHeaders.Names.CONTENT_TYPE);
		assertEquals(1, contentTypes.size());
		assertEquals("application/json; charset=UTF-8", contentTypes.get(0));
		assertEquals("null", responseBody.toString());
	}

	private void sendGetEvent(String path)
    {
		try
		{
		    pl.sendUpstream(new UpstreamMessageEvent(
		    	channel,
		    	new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path),
		    	new InetSocketAddress(1)));
		}
		catch(Throwable t)
		{
			System.out.println(t.getMessage());
		}
    }

	private void sendGetEvent(String path, String body)
    {
		try
		{
			HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
			request.setContent(ChannelBuffers.copiedBuffer(body, Charset.defaultCharset()));
	
		    pl.sendUpstream(new UpstreamMessageEvent(
		    	channel,
		    	request,
		    	new InetSocketAddress(1)));
		}
		catch(Throwable t)
		{
			System.out.println(t.getMessage());
		}
    }
	
	public class DummyRoutes
	extends RouteDeclaration
	{
		private Object controller = new FooBarController();
		private RouteDefaults defaults = new RouteDefaults();

        public void defineRoutes()
        {
        	uri("/foo.{format}", controller, defaults)
        		.action("fooAction", HttpMethod.GET);

        	uri("/bar.{format}", controller, defaults)
        		.action("barAction", HttpMethod.GET);

        	uri("/date.{format}", controller, defaults)
    			.action("dateAction", HttpMethod.GET);

        	uri("/unserializedDefault", controller, defaults)
        		.action("unserializedDefault", HttpMethod.GET);

        	uri("/unserialized", controller, defaults)
        		.action("unserializedAction", HttpMethod.GET);

        	uri("/unserializedToo", controller, defaults)
        		.action("contentHeaderAction", HttpMethod.GET);

        	uri("/serializedString.{format}", controller, defaults)
    		.action("serializedStringAction", HttpMethod.GET);

        	uri("/setBodyAction.html", controller, defaults)
        		.action("setBodyAction", HttpMethod.GET)
        		.format(Format.HTML);
        }
	}
	
	public class FooBarController
	{
		public void fooAction(Request request, Response response)
		{
			// do nothing.
		}
		
		public void barAction(Request request, Response response)
		{
			throw new BadRequestException("foobar'd");
		}

		public Object dateAction(Request request, Response response)
		{
			return request.getBodyAs(Dated.class);
		}

		public String unserializedDefault(Request request, Response response)
		{
			response.noSerialization();
			return "should be text plain, here";
		}

		public String unserializedAction(Request request, Response response)
		{
			response.setContentType("text/html");
			response.noSerialization();
			return "<html><body>Some kinda wonderful!</body></html>";
		}

		public String serializedStringAction(Request request, Response response)
		{
			return request.getRawHeader("returnValue");
		}

		public String contentHeaderAction(Request request, Response response)
		{
			response.addHeader("Content-Type", "text/html");
			response.noSerialization();
			return "<html><body>Wow! What a fabulous HTML body...</body></html>";
		}

		public void setBodyAction(Request request, Response response)
		{
			response.setContentType(ContentType.HTML);
			response.noSerialization();
			response.setBody("<html><body>Arbitrarily set HTML body...</body></html>");
		}
	}

	public class DummyObserver
	extends MessageObserver
	{
		private int receivedCount = 0;
		private int exceptionCount = 0;
		private int successCount = 0;
		private int completeCount = 0;

		@Override
        protected void onReceived(Request request, Response response)
        {
			++receivedCount;
        }

		@Override
        protected void onException(Throwable exception, Request request, Response response)
        {
			++exceptionCount;
        }

		@Override
        protected void onSuccess(Request request, Response response)
        {
			++successCount;
        }

		@Override
        protected void onComplete(Request request, Response response)
        {
			++completeCount;
        }

		public int getReceivedCount()
        {
        	return receivedCount;
        }

		public int getExceptionCount()
        {
        	return exceptionCount;
        }

		public int getSuccessCount()
        {
        	return successCount;
        }

		public int getCompleteCount()
        {
        	return completeCount;
        }
	}

	private class NoopPostprocessor
	implements Postprocessor
	{
		private int callCount = 0;

        @Override
        public void process(Request request, Response response)
        {
        	++callCount;
        }
        
        public int getCallCount()
        {
        	return callCount;
        }
	}

	private class ExceptionPostprocessor
	extends NoopPostprocessor
	{
        @Override
        public void process(Request request, Response response)
        {
        	super.process(request, response);
        	throw new RuntimeException("RuntimeException thrown...");
        }
	}
}
