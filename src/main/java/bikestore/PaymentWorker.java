package bikestore;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class PaymentWorker {
    private static final Gson GSON = new Gson();
    private static final Random RND = new Random();
    private static final int MAX_REINTENTOS = 3; // 1 intento + 3 reintentos = 4 en total

    public static void main(String[] args) throws Exception {
        Connection conn = Broker.connect();
        Channel ch = conn.createChannel();
        Channel pub = conn.createChannel(); // canal aparte para publicar
        Broker.declararTopologia(ch);
        ch.basicQos(1); // toma de 1 mensaje a la vez

        DeliverCallback callback = (tag, delivery) -> {
            Pedido p = GSON.fromJson(new String(delivery.getBody(), StandardCharsets.UTF_8), Pedido.class);
            Broker.log(p.pedidoId, "PaymentWorker recibió el pedido");

            boolean cobrado = false;
            for (int intento = 0; intento <= MAX_REINTENTOS && !cobrado; intento++) {
                if (intento > 0) {
                    Broker.log(p.pedidoId, "REINTENTO " + intento + "/" + MAX_REINTENTOS);
                    try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                cobrado = cobrar(p);
            }

            if (cobrado) {
                p.paymentStatus = "PAID";
                pub.basicPublish(Broker.EXCHANGE, Broker.RK_PAID,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        GSON.toJson(p).getBytes(StandardCharsets.UTF_8));
                Broker.log(p.pedidoId, "PAGO OK -> evento payment.paid publicado");
                ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } else {
                Broker.log(p.pedidoId, "PAGO FALLIDO tras " + MAX_REINTENTOS + " reintentos -> DEAD LETTER QUEUE");
                // requeue=false => RabbitMQ lo enruta a la DLX/DLQ
                ch.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };

        ch.basicConsume(Broker.PAYMENT_QUEUE, false, callback, tag -> {});
        System.out.println("PaymentWorker esperando pedidos...");
    }

    private static boolean cobrar(Pedido p) {
        boolean ok = RND.nextDouble() >= 0.5; // 50 % de fallo
        Broker.log(p.pedidoId, "Cobro con tarjeta: " + (ok ? "APROBADO" : "RECHAZADO"));
        return ok;
    }
}
