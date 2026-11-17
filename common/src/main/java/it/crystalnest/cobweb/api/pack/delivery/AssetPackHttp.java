package it.crystalnest.cobweb.api.pack.delivery;

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
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Serves immutable pack ZIPs over the game listener or a separate HTTP listener.
 */
final class AssetPackHttp implements AutoCloseable {
  /**
   * Base path for the built-in archive endpoint.
   */
  static final String ROUTE = "/cobweb/packs/";

  /**
   * Pipeline name of the timeout used while waiting for request headers.
   */
  private static final String HTTP_TIMEOUT = "cobweb_asset_http_timeout";

  /**
   * Allowed archive file names, consisting of a lowercase SHA-256 digest and the ZIP extension.
   */
  private static final Pattern ARCHIVE = Pattern.compile("[a-f0-9]{64}[.]zip");

  /**
   * Request method prefixes recognized by the shared-port detector, including methods the endpoint rejects.
   */
  private static final List<byte[]> HTTP_METHODS = Stream.of("GET ", "HEAD ", "POST ", "PUT ", "OPTIONS ", "DELETE ", "PATCH ", "TRACE ", "CONNECT ").map(method -> method.getBytes(StandardCharsets.US_ASCII)).toList();

  /**
   * Publication service that leases available archives.
   */
  private final AssetDelivery delivery;

  /**
   * Owned workers for opening archives and reading outbound file chunks off the channel event loop.
   */
  private final DefaultEventExecutorGroup files = new DefaultEventExecutorGroup(2, new DefaultThreadFactory("Cobweb asset files", true));

  /**
   * Owned HTTP channels, including any channel added after shutdown begins.
   */
  private final DefaultChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true);

  /**
   * Maximum number of admitted requests for the built-in HTTP transport.
   */
  private static final int MAX_DOWNLOADS = 8;

  /**
   * Maximum number of requests queued for a transfer slot.
   */
  private static final int MAX_WAITING = 64;

  /**
   * Validated requests waiting for a slot in arrival order, guarded by this transport's monitor.
   */
  private final ArrayDeque<Download> waiting = new ArrayDeque<>();

  /**
   * Number of occupied transfer slots, guarded by this transport's monitor.
   */
  private int activeDownloads;

  /**
   * Owned event-loop group for a separate HTTP listener, or {@code null} until one is started.
   */
  private MultiThreadIoEventLoopGroup listener;

  /**
   * @param delivery publication service that supplies archive leases.
   */
  AssetPackHttp(AssetDelivery delivery) {
    this.delivery = delivery;
  }

  /**
   * Binds a dedicated HTTP listener and waits for the bind to complete.
   *
   * @param bind local address, or an empty string to bind to all interfaces.
   * @param port local HTTP port.
   * @throws IllegalStateException if the listener cannot bind.
   */
  void listen(String bind, int port) {
    listener = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("Cobweb asset HTTP", true), NioIoHandler.newFactory());
    var address = bind.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
    var binding = new ServerBootstrap().group(listener)
      .channel(NioServerSocketChannel.class)
      .childHandler(new ChannelInitializer<>() {
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

  /**
   * Installs the HTTP detector before the Minecraft packet handlers on the game port.
   *
   * @param channel newly accepted TCP channel.
   */
  void detect(Channel channel) {
    channel.pipeline().addLast("cobweb_asset_http_detector", new Detect());
  }

  /**
   * Installs the HTTP pipeline and schedules the 90-second connection deadline on the channel's event loop.
   * The loop is borrowed from the channel, and closing the connection cancels only its deadline task.
   * Both the request and chunked-write handlers use the file workers so opening and streaming archives stay off the channel loop.
   *
   * @param channel connection identified as HTTP or accepted by the separate listener.
   */
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

  /**
   * Closes the HTTP channels and starts shutdown of the owned file workers and separate listener, if present.
   * Minecraft continues to own the game listener and its event loops.
   */
  @Override
  public void close() {
    channels.close();
    files.shutdownGracefully();
    if (listener != null) {
      listener.shutdownGracefully();
    }
  }

  /**
   * Distinguishes an HTTP method prefix from Minecraft traffic without consuming the inspected bytes.
   */
  private final class Detect extends ByteToMessageDecoder {
    /**
     * Waits for an unambiguous prefix and removes the detector to forward the buffered bytes.
     * An HTTP prefix replaces the following Minecraft handlers with the HTTP pipeline.
     *
     * @param context detector context in the shared pipeline.
     * @param input buffered connection bytes.
     * @param output decoded messages, unused because the bytes are forwarded on removal.
     */
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

  /**
   * Validates and serves at most one body-free request per HTTP connection.
   */
  private final class Download extends SimpleChannelInboundHandler<HttpRequest> {
    /**
     * Whether this connection's first request has been handled.
     */
    private boolean requested;

    /**
     * Handler context captured when a request passes validation.
     */
    private ChannelHandlerContext context;

    /**
     * SHA-256 address of the requested archive once validated.
     */
    private String address;

    /**
     * Whether the validated request is for response headers only.
     */
    private boolean head;

    /**
     * Whether this request holds a transfer slot, guarded by the transport's monitor.
     */
    private boolean admitted;

    /**
     * Validates the request and admits or queues it.
     * Additional messages are ignored, and a full queue receives HTTP 503.
     *
     * @param context download handler context.
     * @param request decoded HTTP request.
     */
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
      this.context = context;
      this.address = name.substring(0, 64);
      this.head = HttpMethod.HEAD.equals(request.method());
      context.channel().closeFuture().addListener(future -> release());
      synchronized (AssetPackHttp.this) {
        if (!context.channel().isActive()) {
          return;
        }
        if (activeDownloads < MAX_DOWNLOADS) {
          admit();
        } else if (waiting.size() < MAX_WAITING) {
          waiting.addLast(this);
          // Keep detecting peer disconnects while waiting; no file worker blocks on admission.
          context.channel().config().setAutoRead(true);
        } else {
          fail(context, HttpResponseStatus.SERVICE_UNAVAILABLE);
        }
      }
    }

    /**
     * Reserves a transfer slot and schedules serving on the handler's borrowed file executor.
     * The transport monitor must be held. Closes the connection and returns the slot if scheduling is rejected.
     */
    private void admit() {
      context.channel().config().setAutoRead(false);
      admitted = true;
      activeDownloads++;
      try {
        context.executor().execute(this::serve);
      } catch (RejectedExecutionException failure) {
        context.close();
        release();
      }
    }

    /**
     * Removes this request from the queue or releases its slot, then admits waiting connections in arrival order.
     */
    private void release() {
      synchronized (AssetPackHttp.this) {
        waiting.remove(this);
        if (admitted) {
          admitted = false;
          activeDownloads--;
        }
        while (activeDownloads < MAX_DOWNLOADS && !waiting.isEmpty()) {
          var next = waiting.removeFirst();
          if (next.context.channel().isActive()) {
            next.admit();
          }
        }
      }
    }

    /**
     * Leases the archive for the remaining connection lifetime and sends headers or streams its contents.
     * After handoff, the chunked-write handler owns the file input.
     */
    private void serve() {
      if (!context.channel().isActive()) {
        return;
      }
      var lease = delivery.acquire(address);
      if (lease == null) {
        fail(context, HttpResponseStatus.NOT_FOUND);
        return;
      }
      context.channel().closeFuture().addListener(future -> lease.close());
      var archive = lease.archive();
      var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/zip");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, archive.size());
      if (head) {
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
        Constants.LOGGER.error("Cannot serve published asset archive {}", archive.address(), failure);
        fail(context, HttpResponseStatus.INTERNAL_SERVER_ERROR);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
      Constants.LOGGER.debug("Asset download connection failed", failure);
      context.close();
    }

    /**
     * Sends an empty error response and closes the connection after the write.
     *
     * @param context download handler context.
     * @param status HTTP status for the failure.
     */
    private void fail(ChannelHandlerContext context, HttpResponseStatus status) {
      var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
      context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
