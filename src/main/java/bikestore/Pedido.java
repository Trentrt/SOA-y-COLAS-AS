package bikestore;

public class Pedido {
    public String pedidoId;
    public String cliente;
    public String email;
    public String producto;
    public double total;
    public String paymentStatus; // PENDING | PAID

    public Pedido(String pedidoId, String cliente, String email, String producto, double total) {
        this.pedidoId = pedidoId;
        this.cliente = cliente;
        this.email = email;
        this.producto = producto;
        this.total = total;
        this.paymentStatus = "PENDING";
    }
}