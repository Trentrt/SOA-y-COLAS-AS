package bikestore;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class OrderProducer {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        Connection conn = Broker.connect();
        Channel ch = conn.createChannel();
        Broker.declararTopologia(ch);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/ordenar", ex -> {
            try {
                if (!"POST".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, -1);
                    return;
                }
                // Si no se envía body, se genera un pedido de prueba
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Pedido p = body.isBlank()
                        ? new Pedido(UUID.randomUUID().toString().substring(0, 8),
                        "Juan Pérez", "juan@mail.com", "Bicicleta MTB", 450.0)
                        : GSON.fromJson(body, Pedido.class);
                if (p.pedidoId == null) p.pedidoId = UUID.randomUUID().toString().substring(0, 8);
                p.paymentStatus = "PENDING";

                byte[] json = GSON.toJson(p).getBytes(StandardCharsets.UTF_8);
                synchronized (ch) { // Channel no es thread-safe
                    ch.basicPublish(Broker.EXCHANGE, Broker.RK_ORDER,
                            MessageProperties.PERSISTENT_TEXT_PLAIN, json);
                }
                Broker.log(p.pedidoId, "PEDIDO PUBLICADO en " + Broker.PAYMENT_QUEUE);

                byte[] resp = ("{\"pedidoId\":\"" + p.pedidoId + "\",\"estado\":\"ENCOLADO\"}")
                        .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(202, resp.length); // 202 Accepted = se procesará de forma asíncrona
                ex.getResponseBody().write(resp);
            } catch (Exception e) {
                e.printStackTrace();
                ex.sendResponseHeaders(500, -1);
            } finally {
                ex.close();
            }
        });
        server.start();
        System.out.println("OrderProducer listo: POST http://localhost:8080/ordenar");
    }
}