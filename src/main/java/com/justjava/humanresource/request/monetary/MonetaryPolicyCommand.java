package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record MonetaryPolicyCommand(@NotBlank @Size(max=100) String name,@NotNull RequestType requestType,
        @NotBlank String currency,@NotEmpty @Size(max=30) List<@Valid Band> bands) {
 public enum Target { LINE_MANAGER,GROUPED }
 public record Band(@NotNull @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal lowerBound,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal upperBound,@NotNull Target target,Long groupedVersionId) {}
}
