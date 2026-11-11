package bikestore;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;

public class EmailWorker {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        Connection conn = Broker.connect();
        Channel ch = conn.createChannel();
        Broker.declararTopologia(ch);
        ch.basicQos(1);

        DeliverCallback callback = (tag, delivery) -> {
            Pedido p = GSON.fromJson(new String(delivery.getBody(), StandardCharsets.UTF_8), Pedido.class);
            if ("PAID".equals(p.paymentStatus)) {
                Broker.log(p.pedidoId, "EMAIL enviado a " + p.email
                        + " | Pedido confirmado: " + p.producto + " ($" + p.total + ")");
            } else {
                Broker.log(p.pedidoId, "Email omitido: paymentStatus=" + p.paymentStatus);
            }
            ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        ch.basicConsume(Broker.EMAIL_QUEUE, false, callback, t -> {});
        System.out.println("EmailWorker esperando confirmaciones...");
    }
}