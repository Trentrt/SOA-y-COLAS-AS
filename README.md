# BikeStore Async v1.0

Aplicación asíncrona de pedidos para una tienda de bicicletas con **Java 17 + RabbitMQ**.
ISWZ2202 - Diseño y Arquitectura de Software. Autor: [Tu nombre]

## ¿Cómo funciona?

```
POST /ordenar ──► OrderProducer ──► [payment.queue] ──► PaymentWorker
                                                           │ falla 4 veces (1 intento + 3 reintentos)
                                                           ├──► [payment.dlq]
                                                           │ pago aprobado (PAID)
                                                           ▼
                                                    [email.queue] ──► EmailWorker
```

- **OrderProducer:** API que publica el pedido como JSON y responde `202 Accepted`.
- **PaymentWorker:** simula el cobro (50 % de fallo), reintenta 3 veces y, si sigue fallando, envía el pedido a la **DLQ**.
- **EmailWorker:** simula el email solo si `paymentStatus=PAID`.
- Todos los logs muestran `pedidoId`, `timestamp` y `thread`.

## Requisitos

- JDK 17+
- IntelliJ IDEA
- RabbitMQ Server + Erlang, con el plugin de administración activo:
  `rabbitmq-plugins enable rabbitmq_management`

## Ejecución

1. Verifica RabbitMQ en http://localhost:15672 (usuario `guest`, clave `guest`).
2. En IntelliJ ejecuta, en este orden (triángulo verde junto a `main`), dejando los tres corriendo:
   1. `PaymentWorker`
   2. `EmailWorker`
   3. `OrderProducer`
3. Envía un pedido (CMD):
   ```
   curl -X POST http://localhost:8080/ordenar
   ```
4. Envía 30 pedidos para ver reintentos y DLQ:
   ```
   for /L %i in (1,1,30) do curl -s -X POST http://localhost:8080/ordenar
   ```
5. Revisa la DLQ: http://localhost:15672 → *Queues and Streams* → `payment.dlq` → *Get messages*.

## Resultado esperado

```
pedidoId=019668bb | Cobro con tarjeta: RECHAZADO
pedidoId=019668bb | REINTENTO 1/3
pedidoId=019668bb | Cobro con tarjeta: APROBADO
pedidoId=019668bb | PAGO OK -> evento payment.paid publicado
pedidoId=019668bb | EMAIL enviado a juan@mail.com | Pedido confirmado: Bicicleta MTB ($450.0)
```

Un pedido que falla 4 veces termina así y queda en `payment.dlq` (~6 % de los casos):
```
pedidoId=xxxx | PAGO FALLIDO tras 3 reintentos -> DEAD LETTER QUEUE
```

## Estructura

```
src/main/java/bikestore/
├── Broker.java         # conexión, colas y logger
├── Pedido.java         # modelo del pedido
├── OrderProducer.java  # API POST /ordenar
├── PaymentWorker.java  # cobro, reintentos y DLQ
└── EmailWorker.java    # email si PAID
```
