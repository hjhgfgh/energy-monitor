import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 推送验证客户端。
 *
 * <p>用 JDK 11+ 自带的 java.net.http.WebSocket，不依赖任何第三方库。
 * 相比之下 PowerShell 的 System.Net.WebSockets.ClientWebSocket
 * 对握手响应里的 Connection 头要求过于严格（只接受 "Upgrade"，
 * 而 Tomcat 返回 "upgrade, keep-alive"），会直接报错，不适合做验证工具。
 *
 * <p>运行：java scripts/WsTest.java
 */
public class WsTest {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "ws://localhost:8080/ws/realtime";
        int expect = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        CountDownLatch latch = new CountDownLatch(expect);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        WebSocket socket = client.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("[OPEN] " + url);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        System.out.println("[MSG] " + data);
                        latch.countDown();
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("[ERROR] " + error);
                    }
                })
                .join();

        boolean got = latch.await(25, TimeUnit.SECONDS);
        System.out.println(got ? "[OK] 收到 " + expect + " 条推送" : "[TIMEOUT] 未收满 " + expect + " 条");
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }
}
