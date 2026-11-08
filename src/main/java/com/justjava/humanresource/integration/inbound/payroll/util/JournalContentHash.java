package com.justjava.humanresource.integration.inbound.payroll.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;



public final class JournalContentHash {
    private JournalContentHash() {
    }

    public static String calculate(
            String journalReference,
            String revision,
            String payrollPeriodId,
            String periodStart,
            String periodEnd,
            String currency,
            String headerNarration,
            List<Line> lines) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeString(output, "JUSTHR");
            writeString(output, journalReference);
            writeString(output, revision);
            writeString(output, payrollPeriodId);
            writeString(output, periodStart);
            writeString(output, periodEnd);
            writeString(output, currency);
            writeString(output, headerNarration);
            List<Line> sorted = lines.stream()
                    .sorted(Comparator.comparing(Line::accountCode, Comparator.nullsFirst(String::compareTo))
                            .thenComparing(Line::lineType, Comparator.nullsFirst(String::compareTo))
                            .thenComparing(Line::debit, BigDecimal::compareTo)
                            .thenComparing(Line::credit, BigDecimal::compareTo)
                            .thenComparing(Line::accountName, Comparator.nullsFirst(String::compareTo))
                            .thenComparing(Line::narration, Comparator.nullsFirst(String::compareTo)))
                    .toList();
            output.writeInt(sorted.size());
            for (Line line : sorted) {
                writeString(output, line.accountCode());
                writeString(output, line.accountName());
                writeString(output, line.lineType());
                writeString(output, money(line.debit()));
                writeString(output, money(line.credit()));
                writeString(output, line.narration());
            }
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not calculate payroll journal content hash.", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    public record Line(
            String accountCode,
            String accountName,
            String lineType,
            BigDecimal debit,
            BigDecimal credit,
            String narration) {
    }
}
