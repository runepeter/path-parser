package org.brylex.bench;

public final class XmlFixture {

    private XmlFixture() {
    }

    public static String orders(int orderCount, int itemsPerOrder) {
        StringBuilder sb = new StringBuilder(orderCount * 200);
        sb.append("<orders>");
        for (int i = 0; i < orderCount; i++) {
            sb.append("<order>");
            sb.append("<id>").append("ORD-").append(i).append("</id>");
            sb.append("<customer>Customer ").append(i % 100).append("</customer>");
            sb.append("<total>").append(((i * 31) % 10000) / 100.0).append("</total>");
            sb.append("<items>");
            for (int j = 0; j < itemsPerOrder; j++) {
                sb.append("<item>");
                sb.append("<sku>SKU-").append(i).append('-').append(j).append("</sku>");
                sb.append("<qty>").append(j + 1).append("</qty>");
                sb.append("<price>").append(((i + j * 7) % 1000) / 10.0).append("</price>");
                sb.append("</item>");
            }
            sb.append("</items>");
            sb.append("</order>");
        }
        sb.append("</orders>");
        return sb.toString();
    }
}
