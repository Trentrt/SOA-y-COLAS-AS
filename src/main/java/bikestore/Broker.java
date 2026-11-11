package bikestore;

import com.rabbitmq.client.*;
import java.time.Instant;
import java.util.Map;

public class Broker {
    public static final String EXCHANGE = "bikestore.exchange";
    public static final String DLX = "bikestore.dlx";
    public static final String PAYMENT_QUEUE = "payment.queue";
    public static final String EMAIL_QUEUE = "email.queue";
    public static final String DLQ = "payment.dlq";
    public static final String RK_ORDER = "order.created";
    public static final String RK_PAID = "payment.paid";
    public static final String RK_DEAD = "payment.failed";

    public static Connection connect() throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("localhost");
        return f.newConnection();
    }

    /** Crea intercambiadores y colas (si ya existen, no pasa nada). */
    public static void declararTopologia(Channel ch) throws Exception {
        ch.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true);
        ch.exchangeDeclare(DLX, BuiltinExchangeType.DIRECT, true);

        // Cola de mensajes fallidos (Dead-Letter Queue)
        ch.queueDeclare(DLQ, true, false, false, null);
        ch.queueBind(DLQ, DLX, RK_DEAD);

        // Cola de pagos: lo que se rechace va a la DLX
        Map<String, Object> args = Map.of(
                "x-dead-letter-exchange", DLX,
                "x-dead-letter-routing-key", RK_DEAD);
        ch.queueDeclare(PAYMENT_QUEUE, true, false, false, args);
        ch.queueBind(PAYMENT_QUEUE, EXCHANGE, RK_ORDER);

        // Cola de emails
        ch.queueDeclare(EMAIL_QUEUE, true, false, false, null);
        ch.queueBind(EMAIL_QUEUE, EXCHANGE, RK_PAID);
    }

    public static void log(String pedidoId, String msg) {
        System.out.printf("[%s] [%s] pedidoId=%s | %s%n",
                Instant.now(), Thread.currentThread().getName(), pedidoId, msg);
    }
}