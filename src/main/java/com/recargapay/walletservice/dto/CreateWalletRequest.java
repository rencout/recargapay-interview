package com.recargapay.walletservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWalletRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
}
