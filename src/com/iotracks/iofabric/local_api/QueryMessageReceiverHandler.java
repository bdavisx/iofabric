package com.iotracks.iofabric.local_api;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import java.io.StringReader;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import com.iotracks.iofabric.message_bus.Message;
import com.iotracks.iofabric.message_bus.MessageBus;
import com.iotracks.iofabric.utils.logging.LoggingService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

public class QueryMessageReceiverHandler {
	private final String MODULE_NAME = "Local API";

	public void handle(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception{
		LoggingService.logInfo(MODULE_NAME,"In QueryMessageReceiverHandler : handle");

		HttpHeaders headers = req.headers();

		if (req.getMethod() != POST) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED));
			return;
		}

		if(!(headers.get(HttpHeaders.Names.CONTENT_TYPE).equals("application/json"))){
			ByteBuf	errorMsgBytes = ctx.alloc().buffer();
			String errorMsg = " Incorrect content/data format ";
			errorMsgBytes.writeBytes(errorMsg.getBytes());
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST, errorMsgBytes));
			return;
		}


		ByteBuf msgBytes = req.content();
		String requestBody = msgBytes.toString(io.netty.util.CharsetUtil.US_ASCII);
		LoggingService.logInfo(MODULE_NAME,"body :"+ requestBody);
		JsonReader reader = Json.createReader(new StringReader(requestBody));
		JsonObject jsonObject = reader.readObject();

		if(validateMessageQueryInput(jsonObject) != null){
			ByteBuf	errorMsgBytes = ctx.alloc().buffer();
			String errorMsg = validateMessageQueryInput(jsonObject);
			errorMsgBytes.writeBytes(errorMsg.getBytes());
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST, errorMsgBytes));
			return;
		}

		LoggingService.logInfo(MODULE_NAME,"Validation success... ");
		String receiverId = jsonObject.getString("id");
		long timeframeStart = Long.parseLong(jsonObject.get("timeframestart").toString());
		long timeframeEnd = Long.parseLong(jsonObject.get("timeframeend").toString());
		JsonArray publishersArray = jsonObject.getJsonArray("publishers");

		JsonBuilderFactory factory = Json.createBuilderFactory(null);
		JsonObjectBuilder builder = factory.createObjectBuilder();
		JsonArrayBuilder messagesArray = factory.createArrayBuilder();

		MessageBus bus = MessageBus.getInstance();
		int msgCount = 0;

		for(JsonValue jsonPubValue : publishersArray){
			String publisherId = jsonPubValue.toString();
			List<Message> messageList = bus.messageQuery(publisherId, receiverId, timeframeStart, timeframeEnd);

			if(messageList != null){
				for(Message msg : messageList){
					String msgJson = msg.toString();
					messagesArray.add(msgJson);
					msgCount++;
				}
			}
		}

		builder.add("status", "okay");
		builder.add("count", msgCount);
		builder.add("messages", messagesArray);

		String configData = builder.build().toString();
		LoggingService.logInfo(MODULE_NAME,"Config: "+ configData);
		ByteBuf	bytesData = ctx.alloc().buffer();
		bytesData.writeBytes(configData.getBytes());
		FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, bytesData);
		HttpHeaders.setContentLength(res, bytesData.readableBytes());

		sendHttpResponse( ctx, req, res); 
		return;

	}

	private String validateMessageQueryInput(JsonObject message){
		LoggingService.logInfo(MODULE_NAME,"In validateMessageQuery...");
		if(!message.containsKey("id")){
			LoggingService.logInfo(MODULE_NAME,"id not found");
			return "Error: Missing input field id";
		}

		if(!(message.containsKey("timeframestart") && message.containsKey("timeframeend"))){
			LoggingService.logInfo(MODULE_NAME,"timeframestart or timeframeend not found");
			return "Error: Missing input field timeframe start or end";
		}

		if(!message.containsKey("publishers")){
			LoggingService.logInfo(MODULE_NAME,"Publisher not found");
			return "Error: Missing input field publishers";
		}

		try{
			Long.parseLong(message.get("timeframestart").toString());
		}catch(Exception e){
			return "Error: Invalid value of timeframestart";
		}

		try{
			Long.parseLong(message.get("timeframeend").toString());
		}catch(Exception e){
			return "Error: Invalid value of timeframeend";
		}

		if((message.getString("id").trim().equals(""))) return "Error: Missing input field value id";

		return null;
	}

	private static void sendHttpResponse(
			ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		if (res.getStatus().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaders.setContentLength(res, res.content().readableBytes());
		}

		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}
}