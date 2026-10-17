package com.campus.charity.module.finance;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.validation.constraints.*;

public class FinanceForms {
    @Data public static class Order {
        @NotNull private Long projectId;
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) private BigDecimal amount;
        private Boolean anonymous = false;
        @Size(max=500) private String remark;
        @NotBlank @Size(max=80) private String requestKey;
    }
    @Data public static class Payment {
        @NotBlank @Pattern(regexp="SUCCESS|FAILED") private String result;
        @Size(max=500) private String remark;
        private Long voucherFileId;
    }
    @Data public static class Resubmit {
        private Long voucherFileId;
        @Size(max=500) private String remark;
    }
    @Data public static class Confirmation {
        @NotNull private Long orderId;
        @NotBlank @Pattern(regexp="PASS|REJECT") private String result;
        @NotNull @Digits(integer=10,fraction=2) @DecimalMin("0.01") private BigDecimal checkedAmount;
        private Long voucherFileId;
        @Size(max=1000) private String opinion;
        @NotBlank @Size(max=80) private String requestKey;
    }
    @Data public static class Refund {
        @NotNull private Long donationId;
        @NotBlank @Size(max=1000) private String reason;
        @NotBlank @Size(max=80) private String requestKey;
    }
    @Data public static class Audit {
        @NotBlank @Pattern(regexp="PASS|REJECT") private String result;
        @Size(max=1000) private String opinion;
    }
    @Data public static class Complete {
        @NotNull private Long voucherFileId;
        @NotNull private LocalDateTime completeTime;
    }
}
