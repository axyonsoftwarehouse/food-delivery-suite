package com.foodie.api.orders;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fatura do pedido em PDF (E27). Gera o documento a partir do detalhe do pedido
 * ({@link OrderService#detail}), o mesmo mapa que alimentava o HTML imprimível antigo.
 *
 * <p>Os valores saem em reais no formato brasileiro ({@code R$ 1.234,56}). O PDF não tem valor
 * fiscal — é o comprovante do pedido.
 */
public final class InvoicePdf {
    private static final Color INK = new Color(0x1F, 0x1F, 0x1F);
    private static final Color BODY = new Color(0x33, 0x33, 0x33);
    private static final Color MUTED = new Color(0x77, 0x77, 0x77);
    private static final Color LINE = new Color(0xDD, 0xDD, 0xDD);
    private static final Color HEAD_BG = new Color(0x33, 0x33, 0x33);

    private static final Font TITLE = font(FontFactory.HELVETICA_BOLD, 18, INK);
    private static final Font LABEL = font(FontFactory.HELVETICA_BOLD, 10, BODY);
    private static final Font TEXT = font(FontFactory.HELVETICA, 10, BODY);
    private static final Font SMALL = font(FontFactory.HELVETICA, 8, MUTED);
    private static final Font TABLE_HEAD = font(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
    private static final Font TOTAL = font(FontFactory.HELVETICA_BOLD, 13, INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Map<String, String> STATUS = Map.ofEntries(
        Map.entry("placed", "Pedido recebido"),
        Map.entry("accepted", "Aceito pelo restaurante"),
        Map.entry("ready", "Pronto"),
        Map.entry("assigned", "Entregador atribuído"),
        Map.entry("picked_up", "Retirado pelo entregador"),
        Map.entry("served", "Servido"),
        Map.entry("completed", "Concluído"),
        Map.entry("delivered", "Entregue"),
        Map.entry("rejected", "Recusado pelo restaurante"),
        Map.entry("cancelled", "Cancelado"),
        Map.entry("expired", "Expirou sem aceite"),
        Map.entry("failed", "Falha na entrega"));

    private InvoicePdf() {}

    public static byte[] build(Map<String, Object> order) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 54, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(header(order));
            document.add(items(order));
            document.add(totals(order));
            Paragraph footer = new Paragraph("Documento sem valor fiscal.", SMALL);
            footer.setSpacingBefore(16);
            document.add(footer);
            document.close();
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível gerar a fatura do pedido em PDF", error);
        }
        return out.toByteArray();
    }

    private static Paragraph header(Map<String, Object> order) {
        Paragraph block = new Paragraph();
        Paragraph title = new Paragraph("Foodie · Fatura #" + order.get("id"), TITLE);
        title.setSpacingAfter(10);
        block.add(title);
        block.add(line("Restaurante", text(order.get("restaurant_name"))));
        block.add(line("Data", date(order.get("created_at"))));
        block.add(line("Status", STATUS.getOrDefault(String.valueOf(order.get("status")), text(order.get("status")))));
        String type = orderType(order.get("order_type"));
        if (type != null) block.add(line("Tipo", type));
        Object address = order.get("delivery_address_text");
        if (address != null && !String.valueOf(address).isBlank()) block.add(line("Endereço", String.valueOf(address)));
        return block;
    }

    @SuppressWarnings("unchecked")
    private static PdfPTable items(Map<String, Object> order) {
        PdfPTable table = new PdfPTable(new float[] { 6, 1, 2 });
        table.setWidthPercentage(100);
        table.setSpacingBefore(16);
        table.addCell(head("Item", Element.ALIGN_LEFT));
        table.addCell(head("Qtd", Element.ALIGN_CENTER));
        table.addCell(head("Valor", Element.ALIGN_RIGHT));
        List<Map<String, Object>> items = order.get("items") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        if (items.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Sem itens", TEXT));
            empty.setColspan(3);
            empty.setPadding(6);
            table.addCell(empty);
            return table;
        }
        for (Map<String, Object> item : items) {
            long unit = number(item.get("unit_price_cents"));
            long qty = number(item.get("quantity"));
            table.addCell(body(itemLabel(item), Element.ALIGN_LEFT));
            table.addCell(body(String.valueOf(qty), Element.ALIGN_CENTER));
            table.addCell(body(brl(unit * qty), Element.ALIGN_RIGHT));
        }
        return table;
    }

    private static Paragraph totals(Map<String, Object> order) {
        Paragraph block = new Paragraph();
        block.setSpacingBefore(10);
        block.add(summary("Subtotal", brl(number(order.get("subtotal_cents")))));
        block.add(summary("Entrega", brl(number(order.get("delivery_fee_cents")))));
        long service = number(order.get("service_fee_cents"));
        if (service > 0) block.add(summary("Serviço", brl(service)));
        long tip = number(order.get("tip_cents"));
        if (tip > 0) block.add(summary("Gorjeta", brl(tip)));
        long discount = number(order.get("discount_cents"));
        if (discount > 0) block.add(summary("Desconto", "-" + brl(discount)));
        Paragraph total = new Paragraph(new Chunk("Total: " + brl(number(order.get("total_cents"))), TOTAL));
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(6);
        block.add(total);
        return block;
    }

    private static Paragraph line(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ": ", LABEL));
        paragraph.add(new Chunk(value, TEXT));
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private static Paragraph summary(String label, String value) {
        Paragraph paragraph = new Paragraph(label + ": " + value, TEXT);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.setSpacingAfter(1);
        return paragraph;
    }

    private static PdfPCell head(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEAD));
        cell.setBackgroundColor(HEAD_BG);
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static PdfPCell body(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TEXT));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        return cell;
    }

    private static String itemLabel(Map<String, Object> item) {
        StringBuilder label = new StringBuilder(String.valueOf(item.get("name")));
        Object variation = item.get("variation_name");
        if (variation != null) label.append(" (").append(variation).append(')');
        Object addons = item.get("addons");
        if (addons != null) label.append(" · ").append(addons);
        return label.toString();
    }

    private static String orderType(Object value) {
        if (value == null) return null;
        return switch (String.valueOf(value)) {
            case "delivery" -> "Entrega";
            case "take_away" -> "Retirada no local";
            case "dine_in" -> "Consumo no local";
            default -> String.valueOf(value);
        };
    }

    private static String date(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().format(DATE);
        if (value instanceof LocalDateTime local) return local.format(DATE);
        return value == null ? "—" : String.valueOf(value);
    }

    private static String text(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String brl(long cents) {
        long abs = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return sign + "R$ " + String.format(Locale.ROOT, "%,d", abs / 100).replace(',', '.') + ","
            + String.format(Locale.ROOT, "%02d", abs % 100);
    }

    private static Font font(String family, float size, Color color) {
        return FontFactory.getFont(family, size, Font.NORMAL, color);
    }
}
