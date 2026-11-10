import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Synthetic arithmetic/selection model of audited CheckoutService lines 101-115. No database. */
public class CheckoutSubtotalReproduction {
    record Product(long id, BigDecimal price) {}
    record Line(long productId, int quantity, BigDecimal priceSnapshot) {}
    record Cart(String identity, String state, List<Line> lines) {}
    static BigDecimal quote(Cart cart, Map<Long, Product> products) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Line item : cart.lines()) {
            Product product = products.get(item.productId());
            BigDecimal line = product.price().multiply(BigDecimal.valueOf(item.quantity()));
            subtotal = subtotal.add(line);
        }
        return subtotal;
    }
    static void equal(String expected, BigDecimal actual) {
        if (new BigDecimal(expected).compareTo(actual) != 0) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        var products = Map.of(1L,new Product(1,new BigDecimal("1.00")),
                2L,new Product(2,new BigDecimal("0.07")),3L,new Product(3,new BigDecimal("0.01")));
        var intended = new Cart("intended", "CHECKOUT_PROCESSING", List.of(new Line(1,1,new BigDecimal("0.07"))));
        var earlier = new Cart("earlier", "CHECKOUT_PROCESSING", List.of(new Line(2,1,new BigDecimal("0.07"))));
        equal("1.00",quote(intended,products));
        System.out.println("PASS current product price 1.00 * quantity 1 = 1.00, even with stale snapshot 0.07");
        var selected = List.of(earlier,intended).stream().filter(c -> c.state().equals("CHECKOUT_PROCESSING")).findFirst().orElseThrow();
        equal("0.07",quote(selected,products));
        System.out.println("PASS selecting another processing cart reproduces Android 1.00 vs quote 0.07");
        equal("0.07",quote(new Cart("other-quantity","CHECKOUT_PROCESSING",List.of(new Line(3,7,BigDecimal.ZERO))),products));
        System.out.println("PASS different input product/quantity 0.01 * 7 also reproduces 0.07");
        equal("2.00",quote(new Cart("duplicate","CHECKOUT_PROCESSING",List.of(new Line(1,1,BigDecimal.ZERO),new Line(1,1,BigDecimal.ZERO))),products));
        System.out.println("PASS duplicated 1.00 lines yield 2.00, not 0.07");
        equal("1.07",quote(earlier,products).add(new BigDecimal("1.00")));
        System.out.println("PASS delivery 1.00 changes total to 1.07 while subtotal remains 0.07");
    }
}
