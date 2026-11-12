package it.crystalnest.cobweb.impl.pack.delivery;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpDecoderConfig;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import it.crystalnest.cobweb.Constants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Serves immutable pack ZIPs over the game listener or a separate HTTP listener.
 */
final class AssetPackHttp implements AutoCloseable {
  static final String ROUTE = "/cobweb/packs/";

  private static final String HTTP_TIMEOUT = "cobweb_asset_http_timeout";

  private static final Pattern ARCHIVE = Pattern.compile("[a-f0-9]{64}[.]zip");

  private static final List<byte[]> HTTP_METHODS = List.of("GET ", "HEAD ", "POST ", "PUT ", "OPTIONS ", "DELETE ", "PATCH ", "TRACE ", "CONNECT ").stream()
    .map(method -> method.getBytes(StandardCharsets.US_ASCII))
    .toList();

  private final AssetDelivery delivery;

  private final DefaultEventExecutorGroup files = new DefaultEventExecutorGroup(2, new DefaultThreadFactory("Cobweb asset files", true));

  private final DefaultChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true);

  private final Semaphore downloads = new Semaphore(8);

  private MultiThreadIoEventLoopGroup listener;

  AssetPackHttp(AssetDelivery delivery) {
    this.delivery = delivery;
  }

  void listen(String bind, int port) {
    listener = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("Cobweb asset HTTP", true), NioIoHandler.newFactory());
    var address = bind.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
    var binding = new ServerBootstrap().group(listener)
      .channel(NioServerSocketChannel.class)
      .childHandler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel channel) {
          install(channel);
        }
      })
      .bind(address)
      .awaitUninterruptibly();
    if (!binding.isSuccess()) {
      throw new IllegalStateException("Cannot bind Cobweb asset HTTP listener to " + address, binding.cause());
    }
    channels.add(binding.channel());
    Constants.LOGGER.info("Serving Cobweb asset packs on {}", binding.channel().localAddress());
  }

  void detect(Channel channel) {
    channel.pipeline().addLast("cobweb_asset_http_detector", new Detect());
  }

  private void install(Channel channel) {
    channels.add(channel);
    var deadline = channel.eventLoop().schedule((Runnable) channel::close, 90, TimeUnit.SECONDS);
    channel.closeFuture().addListener(future -> deadline.cancel(false));
    var pipeline = channel.pipeline();
    if (pipeline.get("timeout") != null) {
      pipeline.remove("timeout");
    }
    pipeline.addLast(HTTP_TIMEOUT, new ReadTimeoutHandler(10));
    pipeline.addLast(new HttpServerCodec(new HttpDecoderConfig().setMaxInitialLineLength(1024).setMaxHeaderSize(8192).setMaxChunkSize(4096)));
    pipeline.addLast(files, new ChunkedWriteHandler());
    pipeline.addLast(files, new Download());
  }

  @Override
  public void close() {
    channels.close();
    files.shutdownGracefully();
    if (listener != null) {
      listener.shutdownGracefully();
    }
  }

  private final class Detect extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
      boolean partial = false;
      for (byte[] method : HTTP_METHODS) {
        int matched = 0;
        int limit = Math.min(input.readableBytes(), method.length);
        while (matched < limit && input.getByte(input.readerIndex() + matched) == method[matched]) {
          matched++;
        }
        if (matched == method.length) {
          var pipeline = context.pipeline();
          boolean afterDetector = false;
          for (String name : pipeline.names()) {
            if (name.equals(context.name())) {
              afterDetector = true;
            } else if (afterDetector && pipeline.get(name) != null) {
              pipeline.remove(name);
            }
          }
          install(context.channel());
          pipeline.remove(this);
          return;
        }
        partial |= matched == limit;
      }
      if (!partial) {
        context.pipeline().remove(this);
      }
    }
  }

  private final class Download extends SimpleChannelInboundHandler<HttpRequest> {
    private boolean requested;

    @Override
    protected void channelRead0(ChannelHandlerContext context, HttpRequest request) {
      if (requested) {
        return;
      }
      requested = true;
      context.pipeline().remove(HTTP_TIMEOUT);
      context.channel().config().setAutoRead(false);
      if (!request.decoderResult().isSuccess() || HttpUtil.getContentLength(request, 0) != 0 || HttpUtil.isTransferEncodingChunked(request)) {
        fail(context, HttpResponseStatus.BAD_REQUEST);
        return;
      }
      if (!HttpMethod.GET.equals(request.method()) && !HttpMethod.HEAD.equals(request.method())) {
        fail(context, HttpResponseStatus.METHOD_NOT_ALLOWED);
        return;
      }
      String uri = request.uri();
      String name = uri.startsWith(ROUTE) ? uri.substring(ROUTE.length()) : "";
      if (!ARCHIVE.matcher(name).matches()) {
        fail(context, HttpResponseStatus.NOT_FOUND);
        return;
      }
      if (!downloads.tryAcquire()) {
        fail(context, HttpResponseStatus.SERVICE_UNAVAILABLE);
        return;
      }
      var lease = delivery.acquire(name.substring(0, 64));
      if (lease == null) {
        downloads.release();
        fail(context, HttpResponseStatus.NOT_FOUND);
        return;
      }
      context.channel().closeFuture().addListener(future -> {
        lease.close();
        downloads.release();
      });
      var archive = lease.archive();
      var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/zip");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, archive.size());
      if (HttpMethod.HEAD.equals(request.method())) {
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        return;
      }
      try {
        var file = FileChannel.open(archive.path(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        ChunkedNioFile contents;
        try {
          contents = new ChunkedNioFile(file, 0, archive.size(), 64 * 1024);
        } catch (IOException | RuntimeException failure) {
          file.close();
          throw failure;
        }
        context.write(response);
        context.writeAndFlush(new HttpChunkedInput(contents)).addListener(ChannelFutureListener.CLOSE);
      } catch (IOException | RuntimeException failure) {
        Constants.LOGGER.warn("Cannot serve published asset archive {}", archive.address(), failure);
        fail(context, HttpResponseStatus.INTERNAL_SERVER_ERROR);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
      Constants.LOGGER.debug("Asset download connection failed", failure);
      context.close();
    }

    private void fail(ChannelHandlerContext context, HttpResponseStatus status) {
      var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
      context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
