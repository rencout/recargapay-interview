package com.recargapay.walletservice.dto;

import com.recargapay.walletservice.util.WalletConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWalletRequest {
    
    @NotBlank(message = WalletConstants.USER_ID_REQUIRED_MESSAGE)
    private String userId;
}
